package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.RestAPIUtil;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.RestAPIUtil.assertAuth;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Services")
@Tag("Services3")
class HelmRestApiIT extends AbstractHelmChartIT {

    private static final int REST_API_SERVICE_PORT = 8890;
    private static final @NotNull String REST_API_SERVICE_NAME =
            "hivemq-test-hivemq-platform-rest-" + REST_API_SERVICE_PORT;
    private static final @NotNull String REST_API_CUSTOM_SERVICE_NAME = "rest-api-service";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenRestApiEnabled_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/rest-api-values.yaml");

        var clientList =
                RestAPIUtil.getAllMqttClients(client, platformNamespace, REST_API_SERVICE_NAME, REST_API_SERVICE_PORT);
        assertThat(clientList).isEmpty();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenAuthEnabled_thenCallsEndpoint() throws Exception {
        K8sUtil.createConfigMap(client, platformNamespace, "ese-config-map.yml");
        K8sUtil.createConfigMap(client, platformNamespace, "ese-file-realm-config-map.yml");

        installPlatformChartAndWaitToBeRunning("/files/rest-api-with-auth-values.yaml");

        RestAPIUtil.assertAuth(client, platformNamespace, REST_API_SERVICE_NAME, REST_API_SERVICE_PORT);
        RestAPIUtil.assertAuth(client,
                platformNamespace,
                REST_API_SERVICE_NAME,
                REST_API_SERVICE_PORT,
                "user",
                "wrong-password",
                HttpStatus.SC_UNAUTHORIZED);
        assertAuth(client,
                platformNamespace,
                REST_API_SERVICE_NAME,
                REST_API_SERVICE_PORT,
                "/api/v1/management/backups",
                HttpStatus.SC_FORBIDDEN);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");

        var clientList = RestAPIUtil.getAllMqttClients(client,
                platformNamespace,
                REST_API_CUSTOM_SERVICE_NAME,
                REST_API_SERVICE_PORT);
        assertThat(clientList).isEmpty();
    }
}
