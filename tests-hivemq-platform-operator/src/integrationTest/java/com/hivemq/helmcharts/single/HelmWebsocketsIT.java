package com.hivemq.helmcharts.single;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttWebSocketConfig;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static com.hivemq.client.util.KeyStoreUtil.keyManagerFromKeystore;
import static com.hivemq.client.util.KeyStoreUtil.trustManagerFromKeystore;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;
import static com.hivemq.helmcharts.util.MqttUtil.getBlockingClient;
import static com.hivemq.helmcharts.util.MqttUtil.withDefaultPublishSubscribeRunnable;

@Tag("Services")
@Tag("Services2")
@SuppressWarnings("DuplicatedCode")
class HelmWebsocketsIT extends AbstractHelmChartIT {

    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8002 = "hivemq-test-hivemq-platform-ws-8002";
    private static final int WEBSOCKET_SERVICE_PORT_8002 = 8002;
    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8003 = "hivemq-test-hivemq-platform-ws-8003";
    private static final int WEBSOCKET_SERVICE_PORT_8003 = 8003;
    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8004 = "hivemq-test-hivemq-platform-ws-8004";
    private static final int WEBSOCKET_SERVICE_PORT_8004 = 8004;
    private static final @NotNull String WEBSOCKET_SERVICE_PATH = "/mqtt";
    private static final @NotNull String BROKER_KEYSTORE_PASSWORD = "key-changeme";
    private static final @NotNull String BROKER_KEYSTORE_PRIVATE_PASSWORD = "key-changeme";
    private static final @NotNull String CLIENT_KEYSTORE_PASSWORD = "trust-changeme";

    private @NotNull Path brokerCertificateStore;
    private @NotNull Path clientCertificateStore;

    @BeforeEach
    void setup(@TempDir final @NotNull Path tempDir) throws Exception {
        CertificatesUtil.generateCertificates(tempDir.toFile());
        final var encoder = Base64.getEncoder();

        brokerCertificateStore = tempDir.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(brokerCertificateStore);
        createSecret(client, namespace, "ws-keystore-8003", "keystore", encoder.encodeToString(keystoreContent));
        createSecret(client, namespace, "ws-keystore-8004", "keystore.jks", encoder.encodeToString(keystoreContent));
        createSecret(client,
                namespace,
                "ws-keystore-password-8004",
                "keystore.password",
                encoder.encodeToString(BROKER_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        clientCertificateStore = tempDir.resolve("truststore.jks");
        final var truststoreContent = Files.readAllBytes(clientCertificateStore);
        final var base64TruststoreContent = encoder.encodeToString(truststoreContent);
        createSecret(client, namespace, "ws-truststore-8003", "truststore.jks", base64TruststoreContent);
        createSecret(client, namespace, "ws-truststore-8004", "truststore", base64TruststoreContent);
        createSecret(client,
                namespace,
                "ws-truststore-password-8004",
                "truststore.password",
                encoder.encodeToString(CLIENT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenWebsocketEnabled_thenSendsReceivesMessage() throws Exception {
        installChartsAndWaitForPlatformRunning("/files/websockets-test-values.yaml");

        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8002, WEBSOCKET_SERVICE_PORT_8002);

        final var sslConfig = MqttClientSslConfig.builder()
                .keyManagerFactory(keyManagerFromKeystore(brokerCertificateStore.toFile(),
                        BROKER_KEYSTORE_PASSWORD,
                        BROKER_KEYSTORE_PRIVATE_PASSWORD))
                .trustManagerFactory(trustManagerFromKeystore(clientCertificateStore.toFile(),
                        CLIENT_KEYSTORE_PASSWORD))
                .hostnameVerifier((hostname, session) -> true)
                .build();
        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8003, WEBSOCKET_SERVICE_PORT_8003, sslConfig);
        assertWebSocketListener(WEBSOCKET_SERVICE_NAME_PORT_8004, WEBSOCKET_SERVICE_PORT_8004, sslConfig);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertWebSocketListener(final @NotNull String serviceName, final int servicePort) {
        assertWebSocketListener(serviceName, servicePort, null);
    }

    private void assertWebSocketListener(
            final @NotNull String serviceName, final int servicePort, final @Nullable MqttClientSslConfig sslConfig) {
        MqttUtil.execute(client,
                namespace,
                serviceName,
                servicePort,
                portForward -> getBlockingClient(portForward,
                        "PublishClient",
                        clientBuilder -> clientBuilder.webSocketConfig(MqttWebSocketConfig.builder()
                                .serverPath(WEBSOCKET_SERVICE_PATH)
                                .build()).sslConfig(sslConfig)),
                portForward -> getBlockingClient(portForward,
                        "SubscribeClient",
                        clientBuilder -> clientBuilder.webSocketConfig(MqttWebSocketConfig.builder()
                                .serverPath(WEBSOCKET_SERVICE_PATH)
                                .build()).sslConfig(sslConfig)),
                withDefaultPublishSubscribeRunnable());
    }
}
