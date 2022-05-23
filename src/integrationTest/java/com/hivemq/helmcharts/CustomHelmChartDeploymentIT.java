package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Using locally build images tests that all the artifacts are installed and is possible to send a mqtt message
 */
@Testcontainers
public class CustomHelmChartDeploymentIT {

    @Container
    private static final @NotNull OperatorHelmChartContainer
            container = new OperatorHelmChartContainer("v1.23.4-k3s1",
            "k3s.dockerfile",
            "values/customTestValues.yaml")
            .withCustomImages();


    @Test
    public void withCustomImage_mqttMessagePublishedReceived() throws Exception {
        assertTrue(container.isRunning());
        //System.out.println(Runtime.getRuntime().maxMemory());
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
    }
}
