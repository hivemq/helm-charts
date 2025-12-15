package com.hivemq.helmcharts.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

class HelmControlCenterCredentialsSecretIT extends AbstractHelmControlCenterIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomControlCenterCredentialsSecret_thenAbleToLogin() throws Exception {
        final var encoder = Base64.getEncoder();
        createSecret(client,
                platformNamespace,
                "control-center-credentials",
                Map.of("username-secret-key",
                        encoder.encodeToString("test-username".getBytes(StandardCharsets.UTF_8)),
                        "password-secret-key",
                        // SHA256 of test-usernametest-password
                        encoder.encodeToString("6300801c77089c00a55fd57002e856c6934d3764a1e50cc59e8c99f47e8e10a7".getBytes(
                                StandardCharsets.UTF_8))));
        installPlatformChartAndWaitToBeRunning("/files/control-center-credentials-secret-values.yaml");
        assertLogin(client,
                platformNamespace,
                webDriverContainer,
                CC_SERVICE_NAME_8081,
                CC_SERVICE_PORT_8081,
                "test-username",
                "test-password");
    }
}
