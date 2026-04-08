package com.hivemq.helmcharts.serviceaccount;

import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelmCustomServiceAccountCreateMissingServiceAccountIT extends AbstractHelmCustomServiceAccountIT {

    @Override
    protected @NotNull String getReleaseBaseName() {
        return "custom-sa-create-missing-sa";
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withNonExistingCustomServiceAccountThenCreate_hivemqRunning() throws Exception {
        helmChartContainer.installPlatformOperatorChart(operatorReleaseName, "--namespace", operatorNamespace);
        helmChartContainer.installPlatformChart(platformReleaseName,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        final var hivemqCustomResource =
                K8sUtil.waitForHiveMQPlatformState(client, platformNamespace, platformReleaseName, "ERROR");
        //noinspection unchecked
        assertThat((Map<String, String>) hivemqCustomResource.getAdditionalProperties().get("status")).containsValues(
                "The ServiceAccount and its permissions are invalid: The ServiceAccount '%s' does not exist".formatted(
                        SERVICE_ACCOUNT_NAME),
                "INVALID_SERVICEACCOUNT_PERMISSION");

        // create missing ServiceAccount
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformReleaseName);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }
}
