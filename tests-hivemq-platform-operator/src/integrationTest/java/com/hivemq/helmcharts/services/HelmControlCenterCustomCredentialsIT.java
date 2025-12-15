package com.hivemq.helmcharts.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;

class HelmControlCenterCustomCredentialsIT extends AbstractHelmControlCenterIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomControlCenterUsernameAndPassword_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/control-center-basic-credentials-values.yaml");
        assertLogin(client,
                platformNamespace,
                webDriverContainer,
                CC_SERVICE_NAME_8081,
                CC_SERVICE_PORT_8081,
                "test-username",
                "test-password");
    }
}
