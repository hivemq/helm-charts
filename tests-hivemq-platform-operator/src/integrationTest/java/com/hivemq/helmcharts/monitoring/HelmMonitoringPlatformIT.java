package com.hivemq.helmcharts.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HelmMonitoringPlatformIT extends AbstractHelmMonitoringIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withPlatformMonitoringEnabled_metricsAndDashboardAvailable() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/platform-monitoring-enabled-values.yaml");
        assertPrometheusMetrics("com_hivemq_cluster_nodes_count",
                response -> assertThat(response.data().result()).hasSize(2)
                        .extracting(result -> result.metric().get("pod"), result -> result.value().get(1))
                        .containsExactlyInAnyOrder(tuple(PLATFORM_RELEASE_NAME + "-0", "2"),
                                tuple(PLATFORM_RELEASE_NAME + "-1", "2")));
        assertGrafanaDashboard("HiveMQ Platform (Prometheus)");
    }
}
