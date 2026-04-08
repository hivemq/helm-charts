package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.RestAPIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelmRestApiIT extends AbstractHelmChartIT {

    private static final int REST_API_SERVICE_PORT_8890 = 8890;

    private final @NotNull String restApiServiceName8890 =
            "hivemq-%s-rest-%s".formatted(platformReleaseName, REST_API_SERVICE_PORT_8890);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenRestApiEnabled_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/rest-api-values.yaml");

        assertThat(RestAPIUtil.getAllMqttClients(client,
                platformNamespace,
                restApiServiceName8890,
                REST_API_SERVICE_PORT_8890,
                false)).isEmpty();
    }
}
