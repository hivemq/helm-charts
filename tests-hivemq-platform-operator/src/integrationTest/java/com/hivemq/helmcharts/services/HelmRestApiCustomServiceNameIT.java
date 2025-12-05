package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.RestAPIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelmRestApiCustomServiceNameIT extends AbstractHelmChartIT {

    private static final int REST_API_SERVICE_PORT_8890 = 8890;
    private static final @NotNull String REST_API_CUSTOM_SERVICE_NAME = "rest-api-service";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");

        assertThat(RestAPIUtil.getAllMqttClients(client,
                platformNamespace,
                REST_API_CUSTOM_SERVICE_NAME,
                REST_API_SERVICE_PORT_8890,
                false)).isEmpty();
    }
}
