package com.hivemq.helmcharts.edge;

import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the HiveMQ Edge REST API: authenticates with the chart's default {@code admin}/{@code hivemq} credentials,
 * then queries the {@code /api/v1/management/protocol-adapters/types} endpoint and asserts the expected number of
 * available protocol adapter types.
 */
class HelmEdgeApiIT extends AbstractHelmEdgeIT {

    private static final int EDGE_HTTP_PORT = 8080;
    private static final @NotNull String EDGE_ADMIN_USER = "admin";
    private static final @NotNull String EDGE_ADMIN_PASSWORD = "hivemq";

    private static final @NotNull String AUTH_PATH = "/api/v1/auth/authenticate";
    private static final @NotNull String ADAPTER_TYPES_PATH = "/api/v1/management/protocol-adapters/types";



    private static final @NotNull List<String> AVAILABLE_ADAPTER_TYPES = List.of(
            "mtconnect",
            "bacnetip",
            "s7",
            "file",
            "modbus",
            "http",
            "simulation",
            "eip",
            "opcua",
            "databases",
            "ads");

    @Test
    @Timeout(value = 7, unit = TimeUnit.MINUTES)
    void getAdapterTypes_returnsExpectedNumberOfAdapters() throws Exception {
        // Register the startup-log waiter before install so a fast boot cannot race past us.
        final var edgeStartupLogged = waitForEdgeStartupLog();

        installEdgeChartAndWaitToBeRunning();
        edgeStartupLogged.get(5, TimeUnit.MINUTES);

        try (final var portForward = client.pods()
                .inNamespace(edgeNamespace)
                .withName(EDGE_POD_NAME)
                .portForward(EDGE_HTTP_PORT)) {
            final var baseUri = "http://localhost:" + portForward.getLocalPort();

            final var bearerToken = getBearerToken(baseUri);

            final var adapterTypes = getPathAuthenticated(bearerToken, baseUri)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .log()
                    .ifError()
                    .extract()
                    .jsonPath()
                    .getList("items")
                    .stream()
                    .map(adapter -> ((Map<?, ?>)adapter).get("id").toString())
                    .toList();

            assertThat(adapterTypes)
                    .as("available protocol adapter types")
                    .containsExactlyInAnyOrderElementsOf(AVAILABLE_ADAPTER_TYPES);
        }
    }

    private static Response getPathAuthenticated(String bearerToken, String baseUri) {
        return given().header("Authorization", "Bearer " + bearerToken).when().get(baseUri + ADAPTER_TYPES_PATH);
    }

    private static @NonNull String getBearerToken(String baseUri) {
        final var bearerToken = given().contentType(JSON)
                .body(Map.of("userName", EDGE_ADMIN_USER, "password", EDGE_ADMIN_PASSWORD))
                .when()
                .post(baseUri + AUTH_PATH)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .log()
                .ifError()
                .extract()
                .jsonPath()
                .getString("token");
        assertThat(bearerToken).as("auth token").isNotBlank();
        return bearerToken;
    }
}
