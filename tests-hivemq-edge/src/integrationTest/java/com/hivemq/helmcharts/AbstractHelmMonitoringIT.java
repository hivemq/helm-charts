package com.hivemq.helmcharts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hivemq.helmcharts.util.K8sUtil;
import io.restassured.RestAssured;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.hivemq.helmcharts.util.RestAPIUtil.createBasicAuthHeader;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

public class AbstractHelmMonitoringIT extends AbstractHelmChartIT {

    private static final @NotNull String MONITORING_NAMESPACE = "monitoring";
    private static final @NotNull String MONITORING_RELEASE = "monitoring-stack";

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void setup() throws Exception {
        RestAssured.baseURI = "http://localhost";
        helmChartContainer.addHelmRepo("prometheus-community", "https://prometheus-community.github.io/helm-charts");
        helmChartContainer.installChart(MONITORING_RELEASE,
                "prometheus-community/kube-prometheus-stack",
                "--set",
                "prometheus-node-exporter.hostRootFsMount.enabled=false",
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

    protected static void assertPrometheusMetrics(
            final @NotNull String metric,
            final @NotNull Consumer<PrometheusResponse> assertResponse) {
        await().pollInterval(5, TimeUnit.SECONDS).untilAsserted(() -> {
            try (final var forwarded = K8sUtil.getPortForward(client,
                    MONITORING_NAMESPACE,
                    MONITORING_RELEASE + "-kube-prom-prometheus",
                    9090)) {
                final var response = given().port(forwarded.getLocalPort())
                        .log().all(true)
                        .when()
                            .param("query", metric)
                            .get("/api/v1/query")
                        .then()
                            .log()
                            .all(true)
                            .statusCode(HttpStatus.SC_OK)
                            .extract()
                            .as(PrometheusResponse.class);
                assertResponse.accept(response);
            }
        });
    }

    protected static void assertGrafanaDashboard(final @NotNull String dashboardTitle) {
        await().pollInterval(5, TimeUnit.SECONDS).untilAsserted(() -> {
            try (final var forwarded = K8sUtil.getPortForward(client,
                    MONITORING_NAMESPACE,
                    MONITORING_RELEASE + "-grafana",
                    3000)) {
                given().port(forwarded.getLocalPort())
                        .header("Authorization", createBasicAuthHeader("admin", "prom-operator"))
                        .log().all(true)
                        .when()
                            .get("/api/search")
                        .then()
                            .log().all(true)
                            .statusCode(HttpStatus.SC_OK)
                            .body(containsString(String.format("\"title\":\"%s\"", dashboardTitle)));
            }
        });
    }

    protected record PrometheusResponse(@NotNull String status, @NotNull Data data) {
        public record Data(@JsonProperty("resultType") @NotNull String resultType, @NotNull List<Result> result) {
            public record Result(@NotNull Map<String, String> metric, @NotNull List<Object> value) {
            }
        }
    }
}
