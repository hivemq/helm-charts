package com.hivemq.helmcharts.serviceaccount;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelmCustomServiceAccountCreateMissingPermissionsIT extends AbstractHelmCustomServiceAccountIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withNonExistingPermissionsThenCreate_hivemqRunning() throws Exception {
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);

        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME,
                "--set",
                "hivemqPlatformServiceAccount.create=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.create=false",
                "--namespace",
                operatorNamespace);
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        final var hivemqCustomResource =
                K8sUtil.waitForHiveMQPlatformState(client, platformNamespace, PLATFORM_RELEASE_NAME, "ERROR");
        //noinspection unchecked
        assertThat((Map<String, String>) hivemqCustomResource.getAdditionalProperties().get("status")).containsValues(
                String.format(
                        "The ServiceAccount and its permissions are invalid: Found no ClusterRoleBinding for ServiceAccount '%s'",
                        SERVICE_ACCOUNT_NAME),
                "INVALID_SERVICEACCOUNT_PERMISSION");

        // create missing permissions
        createRole();
        createRoleBinding();

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }
}
