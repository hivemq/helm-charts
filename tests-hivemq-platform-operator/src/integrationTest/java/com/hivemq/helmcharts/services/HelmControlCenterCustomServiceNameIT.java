package com.hivemq.helmcharts.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;

class HelmControlCenterCustomServiceNameIT extends AbstractHelmControlCenterIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");
        assertLogin(client, platformNamespace, webDriverContainer, CC_CUSTOM_SERVICE_NAME, CC_SERVICE_PORT_8081);
    }
}
