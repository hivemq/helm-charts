package com.hivemq.helmcharts.single;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttWebSocketConfig;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hivemq.client.util.KeyStoreUtil.keyManagerFromKeystore;
import static com.hivemq.client.util.KeyStoreUtil.trustManagerFromKeystore;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_TRUSTSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_TRUSTSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

@Tag("Services")
@Tag("Services3")
@SuppressWarnings("DuplicatedCode")
class HelmWebsocketsIT extends AbstractHelmChartIT {

    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8002 = "hivemq-test-hivemq-platform-ws-8002";
    private static final int WEBSOCKET_SERVICE_PORT_8002 = 8002;
    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8003 = "hivemq-test-hivemq-platform-ws-8003";
    private static final int WEBSOCKET_SERVICE_PORT_8003 = 8003;
    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8004 = "hivemq-test-hivemq-platform-ws-8004";
    private static final int WEBSOCKET_SERVICE_PORT_8004 = 8004;
    private static final @NotNull String WEBSOCKET_SERVICE_PATH = "/mqtt";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMtlsWebsocketEnabled_thenSendsReceivesMessage(@TempDir final @NotNull Path tempDir)
            throws Exception {
        CertificatesUtil.generateCertificates(tempDir.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tempDir.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);

        createSecret(client, platformNamespace, "ws-keystore-8003", "keystore", base64KeystoreContent);
        createSecret(client, platformNamespace, "ws-keystore-8004", "keystore.jks", base64KeystoreContent);
        createSecret(client,
                platformNamespace,
                "ws-keystore-password-8004",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        final var truststore = tempDir.resolve("truststore.jks");
        final var truststoreContent = Files.readAllBytes(truststore);
        final var base64TruststoreContent = encoder.encodeToString(truststoreContent);
        createSecret(client, platformNamespace, "ws-truststore-8003", "truststore.jks", base64TruststoreContent);
        createSecret(client, platformNamespace, "ws-truststore-8004", "truststore", base64TruststoreContent);
        createSecret(client,
                platformNamespace,
                "ws-truststore-password-8004",
                "truststore.password",
                encoder.encodeToString(DEFAULT_TRUSTSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        installPlatformChartAndWaitToBeRunning("/files/tls-ws-values.yaml");

        final var sslConfig = MqttClientSslConfig.builder()
                .keyManagerFactory(keyManagerFromKeystore(keystore.toFile(),
                        DEFAULT_KEYSTORE_PASSWORD,
                        DEFAULT_PRIVATE_KEY_PASSWORD))
                .trustManagerFactory(trustManagerFromKeystore(truststore.toFile(), DEFAULT_TRUSTSTORE_PASSWORD))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8002, WEBSOCKET_SERVICE_PORT_8002);
        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8003, WEBSOCKET_SERVICE_PORT_8003, sslConfig);
        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8004, WEBSOCKET_SERVICE_PORT_8004, sslConfig);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMtlsWebsocketEnabledWithDifferentPrivateKey_thenSendsReceivesMessage(@TempDir final @NotNull Path tempDir)
            throws Exception {
        final var keystorePassword = "keystore-password";
        final var privateKeyPassword = "private-key-password";
        final var truststorePassword = "truststore-password";
        CertificatesUtil.generateCertificates(tempDir.toFile(),
                Map.of(ENV_VAR_KEYSTORE_PASSWORD,
                        keystorePassword,
                        ENV_VAR_PRIVATE_KEY_PASSWORD,
                        privateKeyPassword,
                        ENV_VAR_TRUSTSTORE_PASSWORD,
                        truststorePassword));
        final var encoder = Base64.getEncoder();
        final var keystore = tempDir.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);

        createSecret(client, platformNamespace, "ws-keystore-8003", "keystore", base64KeystoreContent);
        createSecret(client, platformNamespace, "ws-keystore-8004", "keystore.jks", base64KeystoreContent);
        createSecret(client,
                platformNamespace,
                "ws-keystore-password-8004",
                Map.of("keystore.password",
                        encoder.encodeToString(keystorePassword.getBytes(StandardCharsets.UTF_8)),
                        "my-private-key.password",
                        encoder.encodeToString(privateKeyPassword.getBytes(StandardCharsets.UTF_8))));

        final var truststore = tempDir.resolve("truststore.jks");
        final var truststoreContent = Files.readAllBytes(truststore);
        final var base64TruststoreContent = encoder.encodeToString(truststoreContent);
        createSecret(client, platformNamespace, "ws-truststore-8003", "truststore.jks", base64TruststoreContent);
        createSecret(client, platformNamespace, "ws-truststore-8004", "truststore", base64TruststoreContent);
        createSecret(client,
                platformNamespace,
                "ws-truststore-password-8004",
                "truststore.password",
                encoder.encodeToString(truststorePassword.getBytes(StandardCharsets.UTF_8)));

        installPlatformChartAndWaitToBeRunning("/files/tls-ws-with-private-key-values.yaml");

        final var sslConfig = MqttClientSslConfig.builder()
                .keyManagerFactory(keyManagerFromKeystore(keystore.toFile(), keystorePassword, privateKeyPassword))
                .trustManagerFactory(trustManagerFromKeystore(truststore.toFile(), truststorePassword))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8002, WEBSOCKET_SERVICE_PORT_8002);
        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8003, WEBSOCKET_SERVICE_PORT_8003, sslConfig);
        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8004, WEBSOCKET_SERVICE_PORT_8004, sslConfig);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertWebSocketListener(final @NotNull String serviceName, final int servicePort) {
        assertWebSocketListener(serviceName, servicePort, null);
    }

    private void assertWebSocketListener(
            final @NotNull String serviceName, final int servicePort, final @Nullable MqttClientSslConfig sslConfig) {
        MqttUtil.assertMessages(client,
                platformNamespace,
                serviceName,
                servicePort,
                clientBuilder -> clientBuilder.webSocketConfig(MqttWebSocketConfig.builder()
                        .serverPath(WEBSOCKET_SERVICE_PATH)
                        .build()).sslConfig(sslConfig));
    }
}
