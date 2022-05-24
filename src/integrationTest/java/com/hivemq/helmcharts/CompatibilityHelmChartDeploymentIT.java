package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
@Testcontainers
public class CompatibilityHelmChartDeploymentIT {

    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    @ParameterizedTest
    @ValueSource(strings = {"v1.20.15-k3s1", "v1.21.10-k3s1", "v1.22.7-k3s1", "v1.23.4-k3s1"})
    public void withHelmVersionDeployment_mqttMessagePublishedReceived(final @NotNull String version) throws Exception {
        try (var container = new OperatorHelmChartContainer(version, "k3s.dockerfile",
                "values/customTestValues.yaml")) {
            container.withCustomImages();
            container.start();
            Mqtt5BlockingClient client = Mqtt5Client.builder()
                    .automaticReconnectWithDefaultConfig()
                    .serverPort(container.getMappedPort(1883))
                    .buildBlocking();

            client.connect();

            var publishes = client.publishes(MqttGlobalPublishFilter.ALL);

            client.subscribeWith().topicFilter("test").send();
            client.publishWith()
                    .topic("test")
                    .payload("Sending Message".getBytes(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE).send();

            Mqtt5Publish receivedMessage = publishes.receive();
            assertTrue(receivedMessage.getPayload().isPresent());
            assertEquals("Sending Message", StandardCharsets.UTF_8.decode(receivedMessage
                    .getPayload().get().asReadOnlyBuffer()).toString());
            container.stop();
        }

    }


}
