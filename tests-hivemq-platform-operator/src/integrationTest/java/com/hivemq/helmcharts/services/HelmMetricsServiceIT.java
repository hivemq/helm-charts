package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.MonitoringUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmMetricsServiceIT extends AbstractHelmChartIT {

    private static final int METRICS_SERVICE_PORT_9499 = 9499;
    private static final @NotNull String METRICS_SERVICE_PATH = "/metrics";

    private final @NotNull String metricsServiceName9499 =
            "hivemq-%s-metrics-%s".formatted(platformReleaseName, METRICS_SERVICE_PORT_9499);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomMetrics_thenMetricsAvailable() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/metrics-values.yaml");

        MqttUtil.assertMessages(client, platformNamespace, defaultMqttServiceName, DEFAULT_MQTT_SERVICE_PORT);

        MonitoringUtil.assertSubscribesPublishesMetrics(client,
                platformNamespace,
                metricsServiceName9499,
                METRICS_SERVICE_PORT_9499,
                METRICS_SERVICE_PATH);
    }
}
