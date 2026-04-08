package com.hivemq.helmcharts.serviceaccount;

import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmCustomServiceAccountDisabledCreationAndValidationIT extends AbstractHelmCustomServiceAccountIT {

    @Override
    protected @NotNull String getReleaseBaseName() {
        return "custom-sa-disabled-validation";
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withCreationAndValidationDisabled_withCorrectExternalPermissions_hivemqRunning()
            throws Exception {
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);
        createRole();
        createRoleBinding();

        helmChartContainer.installPlatformOperatorChart(operatorReleaseName,
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
        helmChartContainer.installPlatformChart(platformReleaseName,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformReleaseName);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }
}
