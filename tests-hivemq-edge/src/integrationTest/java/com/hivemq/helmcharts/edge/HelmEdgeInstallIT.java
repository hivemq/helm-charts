package com.hivemq.helmcharts.edge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmEdgeInstallIT extends AbstractHelmEdgeIT {

    /**
     * Smoke test: install the {@code hivemq-edge} chart, assert that the deployed pod runs the Edge version declared
     * in {@code libs.versions.toml} (passed in via the {@code hivemq.edge.tag} system property), then run the chart's
     * built-in {@code helm test} hook (mqtt-cli connectivity check).
     */
    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void withLocalCharts_edgeRunningAndMqttReachable() throws Exception {
        // Register the version-log waiter BEFORE install so the line cannot be missed if Edge boots fast.
        final var expectedVersion = System.getProperty("hivemq.edge.tag");
        final var versionLogged = waitForEdgeVersionLog(expectedVersion);

        installEdgeChartAndWaitToBeRunning();
        versionLogged.get(5, TimeUnit.MINUTES);

        helmChartContainer.helmTest(EDGE_RELEASE_NAME, edgeNamespace);
    }
}
