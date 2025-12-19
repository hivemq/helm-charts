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
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

class HelmPlatformTlsIT extends AbstractHelmPlatformTlsIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabled_hivemqRunning() throws Exception {
        CertificatesUtil.generateCertificates(tmp.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);
        createSecret(client,
                platformNamespace,
                "mqtts-keystore",
                Map.of("keystore", base64KeystoreContent, "keystore.jks", base64KeystoreContent));

        createSecret(client,
                platformNamespace,
                "mqtts-keystore-password",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        createSecret(client, platformNamespace, "https-keystore", "keystore", encoder.encodeToString(keystoreContent));
        createSecret(client,
                platformNamespace,
                "https-keystore-password",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        installPlatformChartAndWaitToBeRunning("/files/tls-mqtt-values.yaml");

        assertSecretMounted(client, "mqtts-keystore");
        assertSecretMounted(client, "https-keystore");

        final var sslConfig = MqttClientSslConfig.builder()
                .trustManagerFactory(trustManagerFromKeystore(keystore.toFile(), DEFAULT_KEYSTORE_PASSWORD))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        assertMqttListener(DEFAULT_MQTT_SERVICE_NAME, DEFAULT_MQTT_SERVICE_PORT, sslConfig);
        assertMqttListener(MQTT_SERVICE_NAME_1884, MQTT_SERVICE_PORT_1884, sslConfig);
        assertLogin(client,
                platformNamespace,
                webDriverContainer,
                HIVEMQ_CC_SERVICE_NAME,
                HIVEMQ_CC_SERVICE_PORT,
                true);
    }
}
