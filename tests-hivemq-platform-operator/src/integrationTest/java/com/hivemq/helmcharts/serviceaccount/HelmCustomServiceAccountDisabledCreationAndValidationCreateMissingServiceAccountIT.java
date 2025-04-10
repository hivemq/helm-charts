package com.hivemq.helmcharts.serviceaccount;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmCustomServiceAccountDisabledCreationAndValidationCreateMissingServiceAccountIT
        extends AbstractHelmCustomServiceAccountIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withCreateAndValidateDisabled_hivemqRunning() throws Exception {
        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME,
                "--set",
                "hivemqPlatformServiceAccount.create=false",
                "--set",
                "hivemqPlatformServiceAccount.validate=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.create=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.validate=false",
                "--namespace",
                operatorNamespace);
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        // create the ServiceAccount and permissions correctly
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);
        createRole();
        createRoleBinding();

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }
}
