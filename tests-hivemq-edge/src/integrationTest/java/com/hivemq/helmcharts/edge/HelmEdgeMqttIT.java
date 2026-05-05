package com.hivemq.helmcharts.edge;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies end-to-end MQTT functionality of the {@code hivemq-edge} chart: install the chart, port-forward to the
 * StatefulSet pod's MQTT port, then publish and subscribe through real MQTT5 clients and assert the payload arrives.
 * Port-forwarding targets the pod directly because the Edge service is headless ({@code clusterIP: None}).
 */
class HelmEdgeMqttIT extends AbstractHelmEdgeIT {

    private static final int EDGE_MQTT_PORT = 1883;
    private static final @NotNull String TOPIC = "test/edge/smoke";
    private static final byte @NotNull [] PAYLOAD = "edge-mqtt-roundtrip".getBytes(StandardCharsets.UTF_8);

    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void publishSubscribeRoundtrip() throws Exception {
        // Register the startup-log waiter before install so the line cannot be missed if Edge boots fast.
        final var edgeStartupLogged = waitForEdgeStartupLog();

        installEdgeChartAndWaitToBeRunning();
        edgeStartupLogged.get(5, TimeUnit.MINUTES);

        try (final var portForward = client.pods()
                .inNamespace(edgeNamespace)
                .withName(EDGE_POD_NAME)
                .portForward(EDGE_MQTT_PORT)) {
            final var publisher = MqttUtil.getBlockingClient(portForward, "edge-publisher");
            final var subscriber = MqttUtil.getBlockingClient(portForward, "edge-subscriber");
            MqttUtil.connect(publisher);
            MqttUtil.connect(subscriber);
            try (final var publishes = subscriber.publishes(MqttGlobalPublishFilter.ALL)) {
                final var subAck = subscriber.subscribeWith().topicFilter(TOPIC).send();
                assertThat(subAck.getReasonString()).isEmpty();

                final var publishResult = publisher.publishWith().topic(TOPIC).payload(PAYLOAD).send();
                assertThat(publishResult.getError()).isEmpty();

                final var received = publishes.receive(30, TimeUnit.SECONDS);
                assertThat(received).as("Subscriber should receive the published payload on %s", TOPIC).isPresent();
                assertThat(received.get().getPayloadAsBytes()).isEqualTo(PAYLOAD);
            } finally {
                MqttUtil.disconnect(subscriber);
                MqttUtil.disconnect(publisher);
            }
        }
    }
}
