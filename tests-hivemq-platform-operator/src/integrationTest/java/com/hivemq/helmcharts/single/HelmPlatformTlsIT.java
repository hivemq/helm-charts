package com.hivemq.helmcharts.single;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hivemq.client.util.KeyStoreUtil.trustManagerFromKeystore;
import static com.hivemq.helmcharts.testcontainer.DockerImageNames.SELENIUM_DOCKER_IMAGE;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;
import static com.hivemq.helmcharts.util.MqttUtil.getBlockingClient;
import static com.hivemq.helmcharts.util.MqttUtil.withDefaultPublishSubscribeRunnable;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Platform")
@Tag("Tls")
@SuppressWarnings("DuplicatedCode")
class HelmPlatformTlsIT extends AbstractHelmChartIT {

    private static final @NotNull String MQTT_SERVICE_NAME_1883 = "hivemq-test-hivemq-platform-mqtt-1883";
    private static final int MQTT_SERVICE_PORT_1883 = 1883;
    private static final @NotNull String MQTT_SERVICE_NAME_1884 = "hivemq-test-hivemq-platform-mqtt-1884";
    private static final int MQTT_SERVICE_PORT_1884 = 1884;
    private static final @NotNull String BROKER_KEYSTORE_PASSWORD = "key-changeme";
    private static final @NotNull String HIVEMQ_CC_SERVICE_NAME = "hivemq-test-hivemq-platform-cc-8080";
    private static final int HIVEMQ_CC_SERVICE_PORT = 8080;
    private static final @NotNull String LOGIN_BUTTON = ".v-button-hmq-login-button";
    private static final @NotNull String LOGOUT_BUTTON = ".hmq-logout-button";
    private static final @NotNull String TEXT_INPUT_XPATH = "//input[@type='text']";
    private static final @NotNull String PASSWORD_INPUT_XPATH = "//input[@type='password']";

    @SuppressWarnings("resource")
    @Container
    private final @NotNull BrowserWebDriverContainer<?> webDriverContainer =
            new BrowserWebDriverContainer<>(SELENIUM_DOCKER_IMAGE) //
                    .withNetwork(Network.newNetwork()) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway") //
                    .withCapabilities(new ChromeOptions());

    private @NotNull Path brokerCertificateStore;

    @BeforeEach
    void setup(@TempDir final @NotNull Path tempDir) throws Exception {
        CertificatesUtil.generateCertificates(tempDir.toFile());
        final var encoder = Base64.getEncoder();

        brokerCertificateStore = tempDir.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(brokerCertificateStore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);
        createSecret(client,
                namespace,
                "mqtts-keystore",
                Map.of("keystore", base64KeystoreContent, "keystore.jks", base64KeystoreContent));

        createSecret(client,
                namespace,
                "mqtts-keystore-password",
                "keystore.password",
                encoder.encodeToString(BROKER_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        createSecret(client, namespace, "https-keystore", "keystore", encoder.encodeToString(keystoreContent));
        createSecret(client,
                namespace,
                "https-keystore-password",
                "keystore.password",
                encoder.encodeToString(BROKER_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withTls_hivemqRunning() throws Exception {
        installChartsAndWaitForPlatformRunning("/files/tls-test-values.yaml");

        final var statefulSet =
                client.apps().statefulSets().inNamespace(namespace).withName(PLATFORM_RELEASE_NAME).get();
        assertThat(statefulSet).isNotNull();

        assertSecretMounted(statefulSet, "mqtts-keystore");
        assertSecretMounted(statefulSet, "https-keystore");

        final var sslConfig = MqttClientSslConfig.builder()
                .trustManagerFactory(trustManagerFromKeystore(brokerCertificateStore.toFile(),
                        BROKER_KEYSTORE_PASSWORD))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        assertTlsMqttListener(MQTT_SERVICE_NAME_1883, MQTT_SERVICE_PORT_1883, sslConfig);
        assertTlsMqttListener(MQTT_SERVICE_NAME_1884, MQTT_SERVICE_PORT_1884, sslConfig);
        assertTlsControlCenter(HIVEMQ_CC_SERVICE_NAME, HIVEMQ_CC_SERVICE_PORT);
    }

    private void assertTlsMqttListener(
            final @NotNull String serviceName, final int servicePort, final @Nullable MqttClientSslConfig sslConfig) {
        MqttUtil.execute(client,
                namespace,
                serviceName,
                servicePort,
                portForward -> getBlockingClient(portForward,
                        "PublishClient",
                        clientBuilder -> clientBuilder.sslConfig(sslConfig)),
                portForward -> getBlockingClient(portForward,
                        "SubscribeClient",
                        clientBuilder -> clientBuilder.sslConfig(sslConfig)),
                withDefaultPublishSubscribeRunnable());
    }

    @SuppressWarnings("SameParameterValue")
    private void assertTlsControlCenter(final @NotNull String serviceName, final int servicePort) throws Exception {
        try (final var forwarded = K8sUtil.getPortForward(client, namespace, serviceName, servicePort)) {
            final var options = new ChromeOptions();
            options.setAcceptInsecureCerts(true);

            final var webDriver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), options, false);

            webDriver.get("https://host.docker.internal:" + forwarded.getLocalPort());
            final var wait = new WebDriverWait(webDriver, Duration.ofSeconds(30));

            wait.until(webWaitDriver -> {
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).sendKeys("admin");
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).sendKeys("hivemq");
                webWaitDriver.findElement(By.cssSelector(LOGIN_BUTTON)).click();
                return ExpectedConditions.visibilityOfElementLocated(By.cssSelector(LOGOUT_BUTTON));
            });
            webDriver.quit();
        }
    }

    private static void assertSecretMounted(final @NotNull StatefulSet statefulSet, final @NotNull String name) {
        final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
        assertThat(volumes).isNotEmpty();

        final var tlsVolume = statefulSet.getSpec()
                .getTemplate()
                .getSpec()
                .getVolumes()
                .stream()
                .filter(v -> Objects.equals(v.getName(), name))
                .findFirst();
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
