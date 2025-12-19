package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HelmUpgradeExtensionIT extends AbstractHelmBridgeExtensionIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeConfiguration_enableDisableBridge() throws Exception {
        // setup bridge configuration
        final var bridgeConfiguration =
                readResourceFile("bridge-config.xml").replace("<host>remote</host>", "<host>" + ipAddress + "</host>");
        K8sUtil.createConfigMap(client, platformNamespace, "test-bridge-configuration", bridgeConfiguration);

        // deploy chart and wait to be ready
        final var extensionEnabledInitAppFuture = initAppExtensionEnabledFuture();
        final var extensionStartedBrokerFuture = brokerExtensionStartedFuture();
        installPlatformChartAndWaitToBeRunning("/files/bridge-values.yaml");
        await().until(extensionEnabledInitAppFuture::isDone);
        await().until(extensionStartedBrokerFuture::isDone);

        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        // check that extensions are enabled
        assertThat(hivemqCustomResource.get().getAdditionalProperties().get("spec").toString()).matches(
                ".*extensions=\\[.*?enabled=true,.*?id=hivemq-bridge-extension,.*?].*");

        // upgrade chart and wait to be ready
        final var extensionStoppedBrokerFuture = brokerExtensionStoppedFuture();
        final var extensionStoppedInitAppFuture = initAppExtensionStoppedFuture();
        final var extensionUpdateDoneInitAppFuture = initAppExtensionUpdateDoneFuture();
        upgradePlatformChart(PLATFORM_RELEASE_NAME, "-f", "/files/disable-bridge-values.yaml");

        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RESTART_EXTENSIONS"),
                1,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RUNNING"),
                3,
                TimeUnit.MINUTES);
        await().until(extensionStoppedBrokerFuture::isDone);
        await().until(extensionStoppedInitAppFuture::isDone);
        await().until(extensionUpdateDoneInitAppFuture::isDone);

        // check that extensions are disabled
        assertThat(hivemqCustomResource.get().getAdditionalProperties().get("spec").toString()).matches(
                ".*extensions=\\[.*?enabled=false,.*?id=hivemq-bridge-extension,.*?].*");
    }
}
