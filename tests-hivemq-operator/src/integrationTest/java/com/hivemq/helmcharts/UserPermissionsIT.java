package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("UserPermissions")
@Testcontainers
@SuppressWarnings("DuplicatedCode")
class UserPermissionsIT {

    @Container
    private final @NotNull OperatorHelmChartContainer container =
            new OperatorHelmChartContainer(DockerImageNames.K3s.V1_27,
                    "k3s.dockerfile",
                    "values/permissions-values.yaml",
                    "local-hivemq").withLocalImages("hivemq-k8s-test-rootless.tar");

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withLocalImages_mqttMessagePublishedReceived() throws Exception {
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
