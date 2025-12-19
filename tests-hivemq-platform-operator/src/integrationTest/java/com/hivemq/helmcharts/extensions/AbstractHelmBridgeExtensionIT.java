package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.MqttUtil;
import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.MqttUtil.getBlockingClient;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SuppressWarnings("NotNullFieldNotInitialized")
abstract class AbstractHelmBridgeExtensionIT extends AbstractHelmChartIT {

    @Container
    private final @NotNull HiveMQContainer hivemqContainer =
            new HiveMQContainer(OciImages.getImageName("hivemq/hivemq4")).withNetwork(network)
                    .withNetworkAliases("remote")
                    .withLogLevel(Level.DEBUG);

    private static final byte @NotNull [] PAYLOAD = "test".getBytes();
    private static final @NotNull Logger LOG = LoggerFactory.getLogger(AbstractHelmBridgeExtensionIT.class);

    protected @NotNull String ipAddress;

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setUp() {
        final var network =
                hivemqContainer.getContainerInfo().getNetworkSettings().getNetworks().values().stream().findFirst();
        assertThat(network).isPresent();
        ipAddress = Objects.requireNonNull(network.get().getIpAddress());
        LOG.info("Using remote HiveMQ broker IP address for bridge: {}", ipAddress);
    }

    protected void assertMessagesBridged() {
        // assert MQTT messages are bridged
        MqttUtil.execute(client,
                platformNamespace,
                DEFAULT_MQTT_SERVICE_NAME,
                DEFAULT_MQTT_SERVICE_PORT,
                portForward -> getBlockingClient(portForward, "PublishClient"),
                portForward -> getBlockingClient(hivemqContainer.getHost(),
                        hivemqContainer.getMqttPort(),
                        "SubscribeClient"),
                (publishClient, subscribeClient, publishes) -> {
                    final var validTopic = "bridge/topic/test";
                    subscribeClient.subscribeWith().topicFilter(validTopic).send();
                    LOG.info("remote client subscribed");

                    publishClient.publishWith().topic(validTopic).payload(PAYLOAD).send();
                    LOG.info("local client has published");

                    final var publish = publishes.receive(1, TimeUnit.MINUTES);
                    assertThat(publish).isPresent();
                    assertThat(publish.get().getPayloadAsBytes()).isEqualTo(PAYLOAD);
                });
    }

    protected @NotNull CompletableFuture<String> brokerExtensionStartedFuture() {
        return logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX,
                ".*Extension \"HiveMQ Enterprise Bridge Extension\" version .* started successfully.");
    }

    protected @NotNull CompletableFuture<String> brokerExtensionStoppedFuture() {
        return logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX,
                ".*Extension \"HiveMQ Enterprise Bridge Extension\" version .* stopped successfully.");
    }

    protected @NotNull CompletableFuture<String> initAppExtensionEnabledFuture() {
        return waitForInitAppLog("Successfully enabled extension hivemq-bridge-extension");
    }

    protected @NotNull CompletableFuture<String> initAppExtensionStoppedFuture() {
        return waitForInitAppLog("Successfully stopped extension hivemq-bridge-extension");
    }

    protected @NotNull CompletableFuture<String> initAppExtensionUpdateDoneFuture() {
        return waitForInitAppLog("Extension update is done \\(0 errors\\)");
    }
}
