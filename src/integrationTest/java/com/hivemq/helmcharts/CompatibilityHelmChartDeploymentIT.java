package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
@SuppressWarnings("DuplicatedCode")
@Testcontainers
public class CompatibilityHelmChartDeploymentIT {

    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    @ParameterizedTest
    @EnumSource(value = DockerImageNames.K3s.class)
    public void withHelmLocalVersionDeployment_mqttMessagePublishedReceived(final @NotNull DockerImageNames.K3s k3s) throws Exception {
        try (final var container = new OperatorHelmChartContainer(k3s, "k3s.dockerfile", "values/customTestValues.yaml")) {
            container.withLocalImages();
            container.start();
            final var client = Mqtt5Client.builder().automaticReconnectWithDefaultConfig().serverPort(container.getMappedPort(1883)).serverHost(container.getHost()).buildBlocking();

            client.connect();

            try (final var publishes = client.publishes(MqttGlobalPublishFilter.ALL)) {
                client.subscribeWith().topicFilter("test").send();
                client.publishWith().topic("test").payload("Sending Message".getBytes(StandardCharsets.UTF_8)).qos(MqttQos.AT_LEAST_ONCE).send();

                final var receivedMessage = publishes.receive();
                assertTrue(receivedMessage.getPayload().isPresent());
                assertEquals("Sending Message", StandardCharsets.UTF_8.decode(receivedMessage.getPayload().get()).toString());
            }
        }
    }
}
