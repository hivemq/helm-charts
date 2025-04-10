package com.hivemq.helmcharts.serviceaccount;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelmCustomServiceAccountCreateMissingServiceAccountIT extends AbstractHelmCustomServiceAccountIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withNonExistingCustomServiceAccountThenCreate_hivemqRunning() throws Exception {
        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME, "--namespace", operatorNamespace);
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
                        "The ServiceAccount and its permissions are invalid: The ServiceAccount '%s' does not exist",
                        SERVICE_ACCOUNT_NAME),
                "INVALID_SERVICEACCOUNT_PERMISSION");

        // create missing ServiceAccount
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }
}
