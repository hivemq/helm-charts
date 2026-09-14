package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class HelmDataHubIT extends AbstractHelmChartIT {

    private static final int REST_API_SERVICE_PORT = 8890;

    private final @NotNull String restApiServiceName =
            "hivemq-%s-rest-%s".formatted(platformReleaseName, REST_API_SERVICE_PORT);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withDataHubEnabled_messagesValidated() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/data-hub-values.yaml");

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                platformNamespace,
                restApiServiceName,
                REST_API_SERVICE_PORT)) {
            final var baseRestApiEndpoint = "http://localhost:" + forwarded.getLocalPort();

            final var schemaPath = getClass().getResource("/coordinates-schema.json");
            assertThat(schemaPath).isNotNull();
            final var policyPath = getClass().getResource("/coordinates-policy.json");
            assertThat(policyPath).isNotNull();

            given().header("Content-Type", "application/json")
                    .body(new File(schemaPath.getPath()))
                    .when()
                    .post(baseRestApiEndpoint + "/api/v1/data-hub/schemas")
                    .then()
                    .statusCode(HttpStatus.SC_CREATED);

            given().given()
                    .header("Content-Type", "application/json")
                    .body(new File(policyPath.getPath()))
                    .when()
                    .post(baseRestApiEndpoint + "/api/v1/data-hub/data-validation/policies")
                    .then()
                    .statusCode(HttpStatus.SC_CREATED);

            final var body = given().when()
                    .get(baseRestApiEndpoint + "/api/v1/data-hub/data-validation/policies")
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .extract()
                    .body();
            assertThat(body.jsonPath().getList("items")).isNotEmpty();
        }
    }
}
