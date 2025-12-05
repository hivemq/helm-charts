package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmMqttAnnotationsIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT_1884 = 1884;
    private static final @NotNull String MQTT_SERVICE_NAME =
            "hivemq-test-hivemq-platform-mqtt-" + MQTT_SERVICE_PORT_1884;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttAnnotationsEnabled_thenAnnotationsExist() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mqtt-annotations-values.yaml");
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var services = client.services().inNamespace(platformNamespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .allSatisfy(service -> assertThat(service.getMetadata()
                            .getAnnotations()).containsAllEntriesOf(Map.of("test-annotation-key",
                            "test-annotation-value",
                            "test-annotation-key/v1",
                            "test-annotation-value-v1")));
        });
        MqttUtil.assertMessages(client, platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }
}
