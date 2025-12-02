package com.hivemq.helmcharts.monitoring;

import com.hivemq.helmcharts.util.K8sUtil;
import com.marcnuri.helm.Release;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HelmMonitoringOperatorIT extends AbstractHelmMonitoringIT {

    @Override
    protected boolean uninstallPlatformChart() {
        return false;
    }

    @Override
    protected boolean createPlatformNamespace() {
        return false;
    }

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withOperatorMonitoringEnabled_metricsAndDashboardAvailable() {
        final var release = helmUpgradePlatformOperator.withValuesFile(VALUES_PATH.resolve("operator-monitoring-enabled-values.yaml")).call();
        assertThat(release).returns("deployed", Release::getStatus);
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, OPERATOR_RELEASE_NAME);
        assertPrometheusMetrics("hivemq_platform_operator_custom_resource_count",
                response -> assertThat(response.data().result()).hasSize(1)
                        .extracting(result -> result.metric().get("__name__"),
                                result -> result.metric().get("container"),
                                result -> result.metric().get("controller"),
                                result -> result.value().get(1))
                        .containsExactlyInAnyOrder(tuple("hivemq_platform_operator_custom_resource_count",
                                "hivemq-platform-operator",
                                "hivemq-controller",
                                "0")));
        assertGrafanaDashboard("HiveMQ Platform Operator (Prometheus)");
    }
}
