package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.SELENIUM_DOCKER_IMAGE;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

@Tag("Services")
@Tag("Services1")
class HelmControlCenterIT {

    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String HIVEMQ_CC_SERVICE_NAME_8081 = "hivemq-test-hivemq-platform-cc-8081";
    private static final @NotNull String HIVEMQ_CC_SERVICE_NAME_8443 = "hivemq-test-hivemq-platform-cc-8443";
    private static final @NotNull String HIVEMQ_CC_SERVICE_NAME_8444 = "hivemq-test-hivemq-platform-cc-8444";
    private static final @NotNull String KEYSTORE_PASSWORD = "key-changeme";
    private static final @NotNull String NAMESPACE = K8sUtil.getNamespaceName(HelmLicenseSecretIT.class);
    private static final int HIVEMQ_CC_SERVICE_PORT_8081 = 8081;
    private static final int HIVEMQ_CC_SERVICE_PORT_8443 = 8443;
    private static final int HIVEMQ_CC_SERVICE_PORT_8444 = 8444;
    private static final @NotNull String LOGIN_BUTTON = ".v-button-hmq-login-button";
    private static final @NotNull String LOGOUT_BUTTON = ".hmq-logout-button";
    private static final @NotNull String TEXT_INPUT_XPATH = "//input[@type='text']";
    private static final @NotNull String PASSWORD_INPUT_XPATH = "//input[@type='password']";

    private static final @NotNull Network NETWORK = Network.newNetwork();
    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();
    @SuppressWarnings("resource")
    private static final @NotNull BrowserWebDriverContainer<?> WEB_DRIVER_CONTAINER =
            new BrowserWebDriverContainer<>(SELENIUM_DOCKER_IMAGE) //
                    .withNetwork(NETWORK) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway") //
                    .withCapabilities(new ChromeOptions());

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void start() throws Exception {
        WEB_DRIVER_CONTAINER.start();
        HELM_CHART_CONTAINER.start();
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setup() {
        HELM_CHART_CONTAINER.createNamespace(NAMESPACE);
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void shutdown() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                "default");
        HELM_CHART_CONTAINER.stop();
        WEB_DRIVER_CONTAINER.stop();
        NETWORK.close();
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                NAMESPACE);
        HELM_CHART_CONTAINER.deleteNamespace(NAMESPACE);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenControlCenterEnabled_thenAbleToLogin() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/control-center-test-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);

        assertControlCenterListener(HIVEMQ_CC_SERVICE_NAME_8081, HIVEMQ_CC_SERVICE_PORT_8081, false);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabled_thenAbleToLogin(@TempDir final @NotNull Path tmp) throws Exception {
        CertificatesUtil.generateCertificates(tmp.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);

        createSecret(client, NAMESPACE, "cc-keystore-8443", "keystore.jks", encoder.encodeToString(keystoreContent));
        createSecret(client, NAMESPACE, "cc-keystore-8444", "keystore", encoder.encodeToString(keystoreContent));
        createSecret(client,
                NAMESPACE,
                "cc-keystore-password-8443",
                "keystore.password",
                encoder.encodeToString(KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/tls-control-center-test-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);

        assertControlCenterListener(HIVEMQ_CC_SERVICE_NAME_8081, HIVEMQ_CC_SERVICE_PORT_8081, false);
        assertControlCenterListener(HIVEMQ_CC_SERVICE_NAME_8443, HIVEMQ_CC_SERVICE_PORT_8443, true);
        assertControlCenterListener(HIVEMQ_CC_SERVICE_NAME_8444, HIVEMQ_CC_SERVICE_PORT_8444, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenOverrideControlCenter_thenAbleToLogin() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/override-control-center-test-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);

        assertControlCenterListener(HIVEMQ_CC_SERVICE_NAME_8081, HIVEMQ_CC_SERVICE_PORT_8081, "test", "abc123", false);
    }

    private void assertControlCenterListener(
            final @NotNull String serviceName,
            final int port,
            final boolean isTlsEnabled) throws IOException {
        assertControlCenterListener(serviceName, port, "admin", "hivemq", isTlsEnabled);
    }

    private void assertControlCenterListener(
            final @NotNull String serviceName,
            final int port,
            final @NotNull String username,
            final @NotNull String password,
            final boolean isTlsEnabled) throws IOException {
        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client, NAMESPACE, serviceName, port)) {
            final var options = new ChromeOptions();
            options.setAcceptInsecureCerts(true);

            final var webDriver = new RemoteWebDriver(WEB_DRIVER_CONTAINER.getSeleniumAddress(), options, false);

            webDriver.get(String.format("%s://host.docker.internal:%s",
                    isTlsEnabled ? "https" : "http",
                    forwarded.getLocalPort()));
            final var wait = new WebDriverWait(webDriver, Duration.ofSeconds(30));

            wait.until(webWaitDriver -> {
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).sendKeys(username);
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).sendKeys(password);
                webWaitDriver.findElement(By.cssSelector(LOGIN_BUTTON)).click();
                return ExpectedConditions.visibilityOfElementLocated(By.cssSelector(LOGOUT_BUTTON));
            });
            webDriver.quit();
        }
    }
}
