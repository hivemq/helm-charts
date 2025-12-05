package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmMqttIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT_1884 = 1884;
    private static final @NotNull String MQTT_SERVICE_NAME =
            "hivemq-test-hivemq-platform-mqtt-" + MQTT_SERVICE_PORT_1884;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttEnabled_thenSendsReceivesMessage() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mqtt-values.yaml");
        K8sUtil.assertMqttService(client, platformNamespace, MQTT_SERVICE_NAME);
        MqttUtil.assertMessages(client, platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }
}
