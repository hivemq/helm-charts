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
    private static final @NotNull String METRICS_SERVICE_NAME =
            "hivemq-test-hivemq-platform-metrics-" + METRICS_SERVICE_PORT_9499;
    private static final @NotNull String METRICS_SERVICE_PATH = "/metrics";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomMetrics_thenMetricsAvailable() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/metrics-values.yaml");

        MqttUtil.assertMessages(client, platformNamespace, DEFAULT_MQTT_SERVICE_NAME, DEFAULT_MQTT_SERVICE_PORT);

        MonitoringUtil.assertSubscribesPublishesMetrics(client,
                platformNamespace,
                METRICS_SERVICE_NAME,
                METRICS_SERVICE_PORT_9499,
                METRICS_SERVICE_PATH);
    }
}
