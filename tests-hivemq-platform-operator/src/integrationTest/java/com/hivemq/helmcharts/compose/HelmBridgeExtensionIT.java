package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.HIVEMQ_DOCKER_IMAGE;
import static com.hivemq.helmcharts.util.MqttUtil.getBlockingClient;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Extensions")
@Testcontainers
class HelmBridgeExtensionIT extends AbstractHelmChartIT {

    private static final byte @NotNull [] PAYLOAD = "test".getBytes();

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmBridgeExtensionIT.class);

    @Container
    private final @NotNull HiveMQContainer hivemqContainer = new HiveMQContainer(HIVEMQ_DOCKER_IMAGE) //
            .withNetwork(network) //
            .withNetworkAliases("remote") //
            .withLogLevel(Level.DEBUG);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeConfiguration_messageBridged() throws Exception {
        final var hivemqContainerNetwork =
                hivemqContainer.getContainerInfo().getNetworkSettings().getNetworks().values().stream().findFirst();
        assertThat(hivemqContainerNetwork).isPresent();
        final var ipAddress = hivemqContainerNetwork.get().getIpAddress();

        // setup bridge configuration
        final var bridgeConfiguration =
                readResourceFile("bridge-config.xml").replace("<host>remote</host>", "<host>" + ipAddress + "</host>");
        K8sUtil.createConfigMap(client, platformNamespace, "test-bridge-configuration", bridgeConfiguration);

        // deploy chart and wait to be ready
        installPlatformChartAndWaitToBeRunning("/files/bridge-values.yaml");

        // forward the port from the service
        MqttUtil.execute(client,
                platformNamespace, DEFAULT_MQTT_SERVICE_NAME, DEFAULT_MQTT_SERVICE_PORT,
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
}
