package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.util.CertificatesUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

class HelmControlCenterTlsWithPrivateKeyIT extends AbstractHelmControlCenterIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabledWithDifferentPrivateKey_thenAbleToLogin() throws Exception {
        final var keystorePassword = "keystore-password";
        final var privateKeyPassword = "private-key-password";
        CertificatesUtil.generateCertificates(tmp.toFile(),
                Map.of(ENV_VAR_KEYSTORE_PASSWORD, keystorePassword, ENV_VAR_PRIVATE_KEY_PASSWORD, privateKeyPassword));
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);

        createSecret(client, platformNamespace, "cc-keystore-8443", "keystore.jks", base64KeystoreContent);
        createSecret(client, platformNamespace, "cc-keystore-8444", "keystore.jks", base64KeystoreContent);
        createSecret(client,
                platformNamespace,
                "cc-keystore-password-8444",
                Map.of("my-keystore.password",
                        encoder.encodeToString(keystorePassword.getBytes(StandardCharsets.UTF_8)),
                        "my-private-key.password",
                        encoder.encodeToString(privateKeyPassword.getBytes(StandardCharsets.UTF_8))));

        installPlatformChartAndWaitToBeRunning("/files/tls-cc-with-private-key-values.yaml");

        assertLogin(client, platformNamespace, webDriverContainer, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
        assertLogin(client, platformNamespace, webDriverContainer, CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertLogin(client, platformNamespace, webDriverContainer, CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
    }
}
