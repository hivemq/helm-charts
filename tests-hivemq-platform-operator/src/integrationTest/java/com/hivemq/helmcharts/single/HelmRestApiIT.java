package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Services")
@Tag("Services2")
class HelmRestApiIT extends AbstractHelmChartIT {

    private static final int REST_API_SERVICE_PORT = 8890;
    private static final @NotNull String REST_API_SERVICE_NAME = "hivemq-test-hivemq-platform-rest-" + REST_API_SERVICE_PORT;
    private static final @NotNull String REST_API_CUSTOM_SERVICE_NAME = "rest-api-service";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenRestApiEnabled_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/rest-api-values.yaml");

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                platformNamespace,
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
        K8sUtil.createConfigMap(client, platformNamespace, "ese-config-map.yml");
        K8sUtil.createConfigMap(client, platformNamespace, "ese-file-realm-config-map.yml");

        installPlatformChartAndWaitToBeRunning("/files/rest-api-with-auth-values.yaml");

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                platformNamespace,
                REST_API_SERVICE_NAME,
                REST_API_SERVICE_PORT)) {
            final var baseRestApiEndpoint = "http://localhost:" + forwarded.getLocalPort();

            given().header("Authorization", createBasicAuthHeader("test-user", "test-password"))
                    .when()
                    .get(new URI(baseRestApiEndpoint + "/api/v1/mqtt/clients").toURL())
                    .then()
                    .statusCode(HttpStatus.SC_OK);

            given().header("Authorization", createBasicAuthHeader("test-user", "wrong-password"))
                    .when()
                    .get(new URI(baseRestApiEndpoint + "/api/v1/mqtt/clients").toURL())
                    .then()
                    .statusCode(HttpStatus.SC_UNAUTHORIZED);

            given().header("Authorization", createBasicAuthHeader("test-user", "test-password"))
                    .when()
                    .get(new URI(baseRestApiEndpoint + "/api/v1/management/backups").toURL())
                    .then()
                    .statusCode(HttpStatus.SC_FORBIDDEN);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                platformNamespace, REST_API_CUSTOM_SERVICE_NAME,
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

    @SuppressWarnings("SameParameterValue")
    private static @NotNull String createBasicAuthHeader(
            final @NotNull String username, final @NotNull String password) {
        final var credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
