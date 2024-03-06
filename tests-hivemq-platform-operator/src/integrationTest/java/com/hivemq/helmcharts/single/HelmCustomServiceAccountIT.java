package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("CustomConfig")
@Tag("ServiceAccount")
class HelmCustomServiceAccountIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withNonExistingCustomServiceAccountThenCreate_hivemqRunning() throws Exception {
        helmChartContainer.installOperatorChart(OPERATOR_RELEASE_NAME);
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.serviceAccountName=my-custom-sa",
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                namespace);

        final var hivemqCustomResource =
                K8sUtil.waitForHiveMQPlatformState(client, namespace, PLATFORM_RELEASE_NAME, "ERROR");
        //noinspection unchecked
        assertThat((Map<String, String>) hivemqCustomResource.getAdditionalProperties().get("status")).containsValues(
                "The custom resource spec is invalid: The ServiceAccount 'my-custom-sa' does not exist",
                "INVALID_CUSTOM_RESOURCE_SPEC");

        K8sUtil.createServiceAccount(client, namespace, "my-custom-sa");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, PLATFORM_RELEASE_NAME);
    }
}
