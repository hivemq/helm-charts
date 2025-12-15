package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.util.CertificatesUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

class HelmControlCenterTlsIT extends AbstractHelmControlCenterIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabled_thenAbleToLogin() throws Exception {
        CertificatesUtil.generateCertificates(tmp.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);

        createSecret(client,
                platformNamespace,
                "cc-keystore-8443",
                "keystore.jks",
                encoder.encodeToString(keystoreContent));
        createSecret(client,
                platformNamespace,
                "cc-keystore-password-8443",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));
        createSecret(client,
                platformNamespace,
                "cc-keystore-8444",
                "keystore",
                encoder.encodeToString(keystoreContent));

        installPlatformChartAndWaitToBeRunning("/files/tls-cc-values.yaml");

        assertLogin(client, platformNamespace, webDriverContainer, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
        assertLogin(client, platformNamespace, webDriverContainer, CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertLogin(client, platformNamespace, webDriverContainer, CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
    }
}
