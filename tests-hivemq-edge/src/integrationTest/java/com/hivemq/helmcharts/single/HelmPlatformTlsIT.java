package com.hivemq.helmcharts.single;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hivemq.client.util.KeyStoreUtil.trustManagerFromKeystore;
import static com.hivemq.helmcharts.testcontainer.DockerImageNames.SELENIUM_DOCKER_IMAGE;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Platform")
@Tag("Tls")
@Testcontainers
@SuppressWarnings("DuplicatedCode")
class HelmPlatformTlsIT extends AbstractHelmChartIT {

    private static final @NotNull String MQTT_SERVICE_NAME_1884 = "hivemq-test-hivemq-platform-mqtt-1884";
    private static final int MQTT_SERVICE_PORT_1884 = 1884;
    private static final @NotNull String MQTT_SERVICE_NAME_1885 = "hivemq-test-hivemq-platform-mqtt-1885";
    private static final int MQTT_SERVICE_PORT_1885 = 1885;
    private static final @NotNull String MQTT_SERVICE_NAME_1886 = "hivemq-test-hivemq-platform-mqtt-1886";
    private static final int MQTT_SERVICE_PORT_1886 = 1886;
    private static final @NotNull String HIVEMQ_CC_SERVICE_NAME = "hivemq-test-hivemq-platform-cc-8080";
    private static final int HIVEMQ_CC_SERVICE_PORT = 8080;

    @Container
    @SuppressWarnings("resource")
    private static final @NotNull BrowserWebDriverContainer<?> WEB_DRIVER_CONTAINER =
            new BrowserWebDriverContainer<>(SELENIUM_DOCKER_IMAGE) //
                    .withNetwork(network) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway") //
                    .withCapabilities(new ChromeOptions());

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabled_hivemqRunning(@TempDir final @NotNull Path tempDir) throws Exception {
        CertificatesUtil.generateCertificates(tempDir.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tempDir.resolve("keystore.jks");
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
                WEB_DRIVER_CONTAINER,
                HIVEMQ_CC_SERVICE_NAME,
                HIVEMQ_CC_SERVICE_PORT,
                true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabledWithDifferentPrivateKey_hivemqRunning(@TempDir final @NotNull Path tempDir)
            throws Exception {
        final var keystorePassword = "keystore-password";
        final var privateKeyPassword = "private-key-password";
        CertificatesUtil.generateCertificates(tempDir.toFile(),
                Map.of(ENV_VAR_KEYSTORE_PASSWORD, keystorePassword, ENV_VAR_PRIVATE_KEY_PASSWORD, privateKeyPassword));
        final var encoder = Base64.getEncoder();
        final var keystore = tempDir.resolve("keystore.jks");
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

    @SuppressWarnings("SameParameterValue")
    private void assertMqttListener(final @NotNull String serviceName, final int servicePort) {
        assertMqttListener(serviceName, servicePort, null);
    }

    private void assertMqttListener(
            final @NotNull String serviceName, final int servicePort, final @Nullable MqttClientSslConfig sslConfig) {
        MqttUtil.assertMessages(client,
                platformNamespace,
                serviceName,
                servicePort,
                mqttClientModifier -> mqttClientModifier.sslConfig(sslConfig));
    }

    private void assertSecretMounted(final @NotNull KubernetesClient client, final @NotNull String name) {
        final var statefulSet =
                client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
        assertThat(statefulSet).isNotNull();
        final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
        assertThat(volumes).isNotEmpty();

        final var tlsVolume = volumes.stream().filter(v -> Objects.equals(v.getName(), name)).findFirst();
        assertThat(tlsVolume).isPresent();

        final var container = statefulSet.getSpec().getTemplate().getSpec().getContainers().getFirst();
        assertThat(container).isNotNull();

        final var volumeMount = container.getVolumeMounts()
                .stream()
                .filter(vm -> Objects.equals(vm.getName(), name) && Objects.equals(vm.getMountPath(), "/tls-" + name))
                .findFirst();
        assertThat(volumeMount).isPresent();
    }
}
