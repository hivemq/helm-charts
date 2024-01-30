package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Services")
@Tag("Services2")
class HelmRestApiIT extends AbstractHelmChartIT {

    private static final @NotNull String REST_API_SERVICE_NAME = "hivemq-test-hivemq-platform-rest-8890";
    private static final int REST_API_SERVICE_PORT = 8890;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenRestApiEnabled_thenCallsEndpoint() throws Exception {
        installChartsAndWaitForPlatformRunning("/files/rest-api-test-values.yaml");

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                namespace,
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
}
