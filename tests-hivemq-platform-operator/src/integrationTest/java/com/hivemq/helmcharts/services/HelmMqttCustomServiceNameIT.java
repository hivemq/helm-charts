package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmMqttCustomServiceNameIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT_1883 = 1883;
    private static final @NotNull String MQTT_CUSTOM_SERVICE_NAME = "mqtt-service";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenSendsReceivesMessage() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");
        K8sUtil.assertMqttService(client, platformNamespace, MQTT_CUSTOM_SERVICE_NAME);
        MqttUtil.assertMessages(client, platformNamespace, MQTT_CUSTOM_SERVICE_NAME, MQTT_SERVICE_PORT_1883);
    }
}
