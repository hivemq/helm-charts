package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Services")
@Tag("Services2")
class HelmRestApiIT {

    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();
    private static final @NotNull String NAMESPACE = K8sUtil.getNamespaceName(HelmRestApiIT.class);
    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String REST_API_SERVICE_NAME = "hivemq-test-hivemq-platform-rest-8890";
    private static final int REST_API_SERVICE_PORT = 8890;
    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseSetup() {
        HELM_CHART_CONTAINER.start();
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setup() throws Exception {
        HELM_CHART_CONTAINER.createNamespace(NAMESPACE);
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseTearDown() {
        HELM_CHART_CONTAINER.stop();
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                NAMESPACE);
        K8sUtil.waitForNoPodsDeletedInNamespace(client, NAMESPACE);
        HELM_CHART_CONTAINER.deleteNamespace(NAMESPACE);

        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                "default");
        K8sUtil.waitForNoPodsDeletedInNamespace(client, "default");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenRestApiEnabled_thenCallsEndpoint() throws Exception {
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/rest-api-test-values.yaml",
                "--namespace",
                NAMESPACE);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, PLATFORM_RELEASE_NAME);

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                NAMESPACE,
                REST_API_SERVICE_NAME,
                REST_API_SERVICE_PORT)) {
            final var baseRestApiEndpoint = "http://localhost:" + forwarded.getLocalPort();

            final var body = given().when()
                    .get(baseRestApiEndpoint + "/api/v1/mqtt/clients")
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .extract()
                    .body();
            assertThat(body.jsonPath().getList("items")).isEmpty();
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenAuthEnabled_thenCallsEndpoint() throws Exception {
        K8sUtil.createConfigMap(client, NAMESPACE, "ese-config-map.yml");
        K8sUtil.createConfigMap(client, NAMESPACE, "ese-file-realm-config-map.yml");

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/rest-api-test-with-auth-values.yaml",
                "--namespace",
                NAMESPACE);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, PLATFORM_RELEASE_NAME);

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                NAMESPACE,
                REST_API_SERVICE_NAME,
                REST_API_SERVICE_PORT)) {
            final var baseRestApiEndpoint = "http://localhost:" + forwarded.getLocalPort();

            given().header("Authorization", createBasicAuthHeader("test-user", "test-password"))
                    .when()
                    .get(new URL(baseRestApiEndpoint + "/api/v1/mqtt/clients"))
                    .then()
                    .statusCode(HttpStatus.SC_OK);

            given().header("Authorization", createBasicAuthHeader("test-user", "wrong-password"))
                    .when()
                    .get(new URL(baseRestApiEndpoint + "/api/v1/mqtt/clients"))
                    .then()
                    .statusCode(HttpStatus.SC_UNAUTHORIZED);

            given().header("Authorization", createBasicAuthHeader("test-user", "test-password"))
                    .when()
                    .get(new URL(baseRestApiEndpoint + "/api/v1/management/backups"))
                    .then()
                    .statusCode(HttpStatus.SC_FORBIDDEN);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static @NotNull String createBasicAuthHeader(
            final @NotNull String username, final @NotNull String password) {
        final String s = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
