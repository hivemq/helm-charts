package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HelmUpgradeExtensionWithNewConfigIT extends AbstractHelmBridgeExtensionIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "LATEST",
                             disabledReason = "fails regularly with the MINIMUM version, unclear why, could be something with the log watcher")
    void withBridgeConfiguration_updateExtensionWithNewConfig() throws Exception {
        // setup bridge configuration
        final var bridgeConfiguration =
                readResourceFile("bridge-config.xml").replace("<host>remote</host>", "<host>" + ipAddress + "</host>");
        K8sUtil.createConfigMap(client, platformNamespace, "test-bridge-configuration", bridgeConfiguration);

        // deploy chart and wait to be ready
        final var extensionEnabledInitAppFuture1 = initAppExtensionEnabledFuture();
        final var extensionStartedBrokerFuture1 = brokerExtensionStartedFuture();
        installPlatformChartAndWaitToBeRunning("/files/bridge-values.yaml");
        await().until(extensionEnabledInitAppFuture1::isDone);
        await().until(extensionStartedBrokerFuture1::isDone);

        // check that extensions are enabled
        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertThat(hivemqCustomResource.get().getAdditionalProperties().get("spec").toString()).matches(
                ".*extensions=\\[.*?enabled=true,.*?id=hivemq-bridge-extension,.*?].*");

        // create a new config map with a different name
        final var extensionEnabledInitAppFuture2 = initAppExtensionEnabledFuture();
        final var extensionStartedBrokerFuture2 = brokerExtensionStartedFuture();
        K8sUtil.createConfigMap(client, platformNamespace, "updated-test-bridge-configuration", bridgeConfiguration);

        // upgrade chart and wait to be ready
        upgradePlatformChart(PLATFORM_RELEASE_NAME, "-f", "/files/bridge-updated-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, PLATFORM_RELEASE_NAME);
        await().until(extensionEnabledInitAppFuture2::isDone);
        await().until(extensionStartedBrokerFuture2::isDone);

        final var upgradedStatefulSet =
                client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
        assertThat(upgradedStatefulSet.getStatus().getAvailableReplicas()).isEqualTo(1);
    }
}
