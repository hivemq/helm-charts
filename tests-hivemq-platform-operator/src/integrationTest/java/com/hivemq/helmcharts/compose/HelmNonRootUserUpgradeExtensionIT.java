package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("NonRootUser")
class HelmNonRootUserUpgradeExtensionIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void updateExtensionConfigMap_withNonRootUser_rollingRestart() throws Exception {
        helmChartContainer.installOperatorChart(OPERATOR_RELEASE_NAME,
                "-f",
                "/files/operator-non-root-user-values.yaml");

        final var tracingConfigMap = K8sUtil.createConfigMap(client, namespace, "distributed-tracing-config-map.yml");
        assertThat(tracingConfigMap).isNotNull();
        final var extensionStartedFuture = helmChartContainer.getLogWaiter()
                .waitFor(PLATFORM_RELEASE_NAME + "-0",
                        ".*Extension \"HiveMQ Enterprise Distributed Tracing Extension\" version .* started successfully.");

        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/platform-non-root-user-with-tracing-extension-values.yaml",
                "--namespace",
                namespace);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, PLATFORM_RELEASE_NAME);
        await().atMost(1, TimeUnit.MINUTES).until(extensionStartedFuture::isDone);

        K8sUtil.updateConfigMap(client, namespace, "distributed-tracing-config-map-update.yml");
        final var configurationUpdatedFuture = helmChartContainer.getLogWaiter()
                .waitFor(PLATFORM_RELEASE_NAME + "-0",
                        ".*HiveMQ Enterprise Distributed Tracing Extension: Successfully updated configuration from '/opt/hivemq/extensions/hivemq-distributed-tracing-extension/conf/config.xml'.");

        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, namespace, PLATFORM_RELEASE_NAME);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RESTART_EXTENSIONS"),
                3,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 3, TimeUnit.MINUTES);
        await().atMost(1, TimeUnit.MINUTES).until(configurationUpdatedFuture::isDone);
    }
}
