package com.hivemq.helmcharts.edge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmEdgeInstallIT extends AbstractHelmEdgeIT {

    /**
     * Smoke test: install the {@code hivemq-edge} chart, wait for the StatefulSet pod to be Ready, then run the
     * chart's built-in {@code helm test} hook (mqtt-cli connectivity check).
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withLocalCharts_edgeRunningAndMqttReachable() throws Exception {
        installEdgeChartAndWaitToBeRunning();
        helmChartContainer.helmTest(EDGE_RELEASE_NAME, edgeNamespace);
    }
}
