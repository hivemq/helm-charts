package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.event.Level;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.HIVEMQ_DOCKER_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Extensions")
@Tag("Extensions2")
@Testcontainers
@SuppressWarnings("DuplicatedCode")
class HelmUpgradeExtensionIT extends AbstractHelmChartIT {

    @Container
    private static final @NotNull HiveMQContainer HIVEMQ_CONTAINER = new HiveMQContainer(HIVEMQ_DOCKER_IMAGE) //
            .withNetwork(network) //
            .withNetworkAliases("remote") //
            .withLogLevel(Level.DEBUG);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeConfiguration_enableDisableBridge() throws Exception {
        final var hivemqContainerNetwork =
                HIVEMQ_CONTAINER.getContainerInfo().getNetworkSettings().getNetworks().values().stream().findFirst();
        assertThat(hivemqContainerNetwork).isPresent();

        // setup bridge configuration
        final var bridgeConfiguration = readResourceFile("bridge-config.xml").replace("<host>remote</host>",
                "<host>" + hivemqContainerNetwork.get().getIpAddress() + "</host>");
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
        helmChartContainer.upgradePlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/disable-bridge-values.yaml",
                "--namespace",
                platformNamespace);

        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RESTART_EXTENSIONS"),
                1,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RUNNING"), 3, TimeUnit.MINUTES);
        await().until(extensionStoppedBrokerFuture::isDone);
        await().until(extensionStoppedInitAppFuture::isDone);
        await().until(extensionUpdateDoneInitAppFuture::isDone);

        // check that extensions are disabled
        assertThat(hivemqCustomResource.get().getAdditionalProperties().get("spec").toString()).matches(
                ".*extensions=\\[.*?enabled=false,.*?id=hivemq-bridge-extension,.*?].*");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeConfiguration_updateExtensionWithNewConfig() throws Exception {
        final var hivemqContainerNetwork =
                HIVEMQ_CONTAINER.getContainerInfo().getNetworkSettings().getNetworks().values().stream().findFirst();
        assertThat(hivemqContainerNetwork).isPresent();

        // setup bridge configuration
        final var bridgeConfiguration = readResourceFile("bridge-config.xml").replace("<host>remote</host>",
                "<host>" + hivemqContainerNetwork.get().getIpAddress() + "</host>");
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
        helmChartContainer.upgradePlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/bridge-updated-values.yaml",
                "--namespace",
                platformNamespace);
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, PLATFORM_RELEASE_NAME);
        await().until(extensionEnabledInitAppFuture2::isDone);
        await().until(extensionStartedBrokerFuture2::isDone);

        final var upgradedStatefulSet =
                client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
        assertThat(upgradedStatefulSet.getStatus().getAvailableReplicas()).isEqualTo(1);
    }

    private @NotNull CompletableFuture<String> brokerExtensionStartedFuture() {
        return logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX,
                ".*Extension \"HiveMQ Enterprise Bridge Extension\" version .* started successfully.");
    }

    private @NotNull CompletableFuture<String> brokerExtensionStoppedFuture() {
        return logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX,
                ".*Extension \"HiveMQ Enterprise Bridge Extension\" version .* stopped successfully.");
    }

    private @NotNull CompletableFuture<String> initAppExtensionEnabledFuture() {
        return waitForInitAppLog("Successfully enabled extension hivemq-bridge-extension");
    }

    private @NotNull CompletableFuture<String> initAppExtensionStoppedFuture() {
        return waitForInitAppLog("Successfully stopped extension hivemq-bridge-extension");
    }

    private @NotNull CompletableFuture<String> initAppExtensionUpdateDoneFuture() {
        return waitForInitAppLog("Extension update is done \\(0 errors\\)");
    }
}
