package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
@Testcontainers
class CompatibilityHelmChartDeploymentIT {

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void withHelmLocalVersionDeployment_mqttMessagePublishedReceived() throws Exception {
        try (final var container = new OperatorHelmChartContainer("values/test-values.yaml", "test-hivemq")) {
            container.withLocalImages();
            container.start();
            final var client = Mqtt5Client.builder()
                    .automaticReconnectWithDefaultConfig()
                    .serverPort(container.getMappedPort(1883))
                    .serverHost(container.getHost())
                    .buildBlocking();

            client.connect();

            try (final var publishes = client.publishes(MqttGlobalPublishFilter.ALL)) {
                client.subscribeWith().topicFilter("test").send();
                client.publishWith()
                        .topic("test")
                        .payload("Sending Message".getBytes(StandardCharsets.UTF_8))
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .send();

                final var receivedMessage = publishes.receive();
                assertThat(receivedMessage.getPayload()).isPresent()
                        .hasValueSatisfying(payload -> assertThat(StandardCharsets.UTF_8.decode(payload).toString()) //
                                .isEqualTo("Sending Message"));
            }
        }
    }
}
