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
        // Register both log waiters BEFORE install: the chart's readiness probe means the install returns
        // only after Edge has logged its start-up, so a waiter registered afterwards never sees the line.
        final var expectedVersion = System.getProperty("hivemq.edge.tag");
        final var versionLogged = waitForEdgeVersionLog(expectedVersion);
        final var edgeStartupLogged = waitForEdgeStartupLog();

        installEdgeChartAndWaitToBeRunning();
        versionLogged.get(5, TimeUnit.MINUTES);
        edgeStartupLogged.get(5, TimeUnit.MINUTES);

        helmChartContainer.testRelease(EDGE_RELEASE_NAME, edgeNamespace);
    }
}
