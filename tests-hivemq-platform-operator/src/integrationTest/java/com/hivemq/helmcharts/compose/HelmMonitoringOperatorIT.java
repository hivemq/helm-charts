package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.RestAPIUtil.createBasicAuthHeader;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

class HelmMonitoringOperatorIT extends AbstractHelmChartIT {

    private static final @NotNull String MONITORING_NAMESPACE = "monitoring";
    private static final @NotNull String MONITORING_RELEASE = "monitoring-stack";
    private static final @NotNull String DASHBOARD_UID = "pmawgrlGk__";

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

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void setup() throws Exception {
        helmChartContainer.addHelmRepo("prometheus-community", "https://prometheus-community.github.io/helm-charts");
        helmChartContainer.installChart(MONITORING_RELEASE,
                "prometheus-community/kube-prometheus-stack",
                "-n",
                MONITORING_NAMESPACE,
                "--create-namespace");
        K8sUtil.waitForPodStateRunning(client,
                MONITORING_NAMESPACE,
                Map.of("app.kubernetes.io/instance", "monitoring-stack"));
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void tearDown() throws Exception {
        helmChartContainer.uninstallRelease(MONITORING_RELEASE, MONITORING_NAMESPACE, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withMonitoringEnabled_metricsAndDashboardAvailable() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning("/files/operator-monitoring-enabled-values.yaml");
        assertPlatformOperatorPrometheusMetrics(List.of("custom_resource_count",
                "custom_resource_namespaces_count",
                "custom_resource_state_count"));
        assertPlatformOperatorGrafanaDashboard();
    }

    private static void assertPlatformOperatorPrometheusMetrics(final @NotNull List<String> metrics) {
        for (final String metric : metrics) {
            await().pollInterval(5, TimeUnit.SECONDS).untilAsserted(() -> {
                try (final var forwarded = K8sUtil.getPortForward(client,
                        MONITORING_NAMESPACE,
                        MONITORING_RELEASE + "-kube-prom-prometheus",
                        9090)) {
                    given().baseUri("http://localhost")
                            .port(forwarded.getLocalPort())
                            .log().all(true)
                            .when()
                                .param("query", metric)
                                .get("/api/v1/query")
                            .then()
                                .statusCode(HttpStatus.SC_OK)
                                .body(containsString(metric));
                }
            });
        }
    }

    private static void assertPlatformOperatorGrafanaDashboard() {
        await().pollInterval(5, TimeUnit.SECONDS).untilAsserted(() -> {
            try (final var forwarded = K8sUtil.getPortForward(client,
                    MONITORING_NAMESPACE,
                    MONITORING_RELEASE + "-grafana",
                    3000)) {
                final var jsonPathResponse = given().baseUri("http://localhost")
                        .port(forwarded.getLocalPort())
                        .header("Authorization", createBasicAuthHeader("admin", "prom-operator"))
                        .log().all(true)
                        .when()
                            .get("/api/dashboards/uid/" + DASHBOARD_UID)
                        .then()
                            .statusCode(HttpStatus.SC_OK)
                        .extract().jsonPath();

                assertThat(jsonPathResponse).isNotNull();
                assertThat(jsonPathResponse.getString("dashboard.title")).isNotNull()
                        .isEqualTo("HiveMQ Platform Operator (Prometheus)");
            }
        });
    }
}
