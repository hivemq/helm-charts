package com.hivemq.helmcharts.single;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttWebSocketConfig;
import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static com.hivemq.helmcharts.util.MqttUtil.getBlockingClient;
import static com.hivemq.helmcharts.util.MqttUtil.withDefaultPublishSubscribeRunnable;

@Tag("Services")
@Tag("Services2")
@SuppressWarnings("DuplicatedCode")
class HelmWebsocketsIT {

    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String NAMESPACE = K8sUtil.getNamespaceName(HelmWebsocketsIT.class);
    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8002 = "hivemq-test-hivemq-platform-ws-8002";
    private static final int WEBSOCKET_SERVICE_PORT_8002 = 8002;
    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8003 = "hivemq-test-hivemq-platform-ws-8003";
    private static final int WEBSOCKET_SERVICE_PORT_8003 = 8003;
    private static final @NotNull String WEBSOCKET_SERVICE_NAME_PORT_8004 = "hivemq-test-hivemq-platform-ws-8004";
    private static final int WEBSOCKET_SERVICE_PORT_8004 = 8004;
    private static final @NotNull String WEBSOCKET_SERVICE_PATH = "/mqtt";
    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseSetup() {
        HELM_CHART_CONTAINER.start();
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseTearDown() {
        HELM_CHART_CONTAINER.stop();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setup() throws Exception {
        HELM_CHART_CONTAINER.createNamespace(NAMESPACE);
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, NAMESPACE, true);
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME, "default");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMtlsWebsocketEnabled_thenSendsReceivesMessage(@TempDir final @NotNull Path tempDir)
            throws Exception {
        CertificatesUtil.generateCertificates(tempDir.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tempDir.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);

        createSecret(client, NAMESPACE, "ws-keystore-8003", "keystore", base64KeystoreContent);
        createSecret(client, NAMESPACE, "ws-keystore-8004", "keystore.jks", base64KeystoreContent);
        createSecret(client,
                NAMESPACE,
                "ws-keystore-password-8004",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        final var truststore = tempDir.resolve("truststore.jks");
        final var truststoreContent = Files.readAllBytes(truststore);
        final var base64TruststoreContent = encoder.encodeToString(truststoreContent);
        createSecret(client, NAMESPACE, "ws-truststore-8003", "truststore.jks", base64TruststoreContent);
        createSecret(client, NAMESPACE, "ws-truststore-8004", "truststore", base64TruststoreContent);
        createSecret(client,
                NAMESPACE,
                "ws-truststore-password-8004",
                "truststore.password",
                encoder.encodeToString(DEFAULT_TRUSTSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/tls-ws-test-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, PLATFORM_RELEASE_NAME);

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

        createSecret(client, NAMESPACE, "ws-keystore-8003", "keystore", base64KeystoreContent);
        createSecret(client, NAMESPACE, "ws-keystore-8004", "keystore.jks", base64KeystoreContent);
        createSecret(client,
                NAMESPACE,
                "ws-keystore-password-8004",
                Map.of("keystore.password",
                        encoder.encodeToString(keystorePassword.getBytes(StandardCharsets.UTF_8)),
                        "my-private-key.password",
                        encoder.encodeToString(privateKeyPassword.getBytes(StandardCharsets.UTF_8))));

        final var truststore = tempDir.resolve("truststore.jks");
        final var truststoreContent = Files.readAllBytes(truststore);
        final var base64TruststoreContent = encoder.encodeToString(truststoreContent);
        createSecret(client, NAMESPACE, "ws-truststore-8003", "truststore.jks", base64TruststoreContent);
        createSecret(client, NAMESPACE, "ws-truststore-8004", "truststore", base64TruststoreContent);
        createSecret(client,
                NAMESPACE,
                "ws-truststore-password-8004",
                "truststore.password",
                encoder.encodeToString(truststorePassword.getBytes(StandardCharsets.UTF_8)));

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/tls-ws-with-private-key-test-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, PLATFORM_RELEASE_NAME);

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
        MqttUtil.execute(client,
                NAMESPACE,
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
