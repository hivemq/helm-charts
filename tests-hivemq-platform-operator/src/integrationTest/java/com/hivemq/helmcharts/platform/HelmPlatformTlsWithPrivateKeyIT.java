package com.hivemq.helmcharts.platform;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.helmcharts.util.CertificatesUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hivemq.client.util.KeyStoreUtil.trustManagerFromKeystore;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

class HelmPlatformTlsWithPrivateKeyIT extends AbstractHelmPlatformTlsIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabledWithDifferentPrivateKey_hivemqRunning() throws Exception {
        final var keystorePassword = "keystore-password";
        final var privateKeyPassword = "private-key-password";
        CertificatesUtil.generateCertificates(tmp.toFile(),
                Map.of(ENV_VAR_KEYSTORE_PASSWORD, keystorePassword, ENV_VAR_PRIVATE_KEY_PASSWORD, privateKeyPassword));
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);

        createSecret(client, platformNamespace, "mqtt-keystore-1884", "keystore.jks", base64KeystoreContent);
        createSecret(client, platformNamespace, "mqtt-keystore-1885", "keystore.jks", base64KeystoreContent);
        createSecret(client,
                platformNamespace,
                "mqtt-keystore-password-1885",
                Map.of("keystore.password",
                        encoder.encodeToString(keystorePassword.getBytes(StandardCharsets.UTF_8)),
                        "my-private-key.password",
                        encoder.encodeToString(privateKeyPassword.getBytes(StandardCharsets.UTF_8))));

        installPlatformChartAndWaitToBeRunning("/files/tls-mqtt-with-private-key-values.yaml");

        assertSecretMounted(client, "mqtt-keystore-1884");
        assertSecretMounted(client, "mqtt-keystore-1885");

        final var sslConfig = MqttClientSslConfig.builder()
                .trustManagerFactory(trustManagerFromKeystore(keystore.toFile(), keystorePassword))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        assertMqttListener(MQTT_SERVICE_NAME_1884, MQTT_SERVICE_PORT_1884, sslConfig);
        assertMqttListener(MQTT_SERVICE_NAME_1885, MQTT_SERVICE_PORT_1885, sslConfig);
        assertMqttListener(MQTT_SERVICE_NAME_1886, MQTT_SERVICE_PORT_1886);
    }
}
