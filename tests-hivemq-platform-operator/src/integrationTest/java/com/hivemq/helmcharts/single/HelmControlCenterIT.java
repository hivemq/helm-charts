package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.Network;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.SELENIUM_DOCKER_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Services")
@Tag("Services1")
@SuppressWarnings("DuplicatedCode")
class HelmControlCenterIT {

    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    private static final @NotNull String HIVEMQ_CC_SERVICE_NAME = "hivemq-test-hivemq-platform-cc-8081";
    private static final int HIVEMQ_CC_SERVICE_PORT = 8081;
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
    public static void setup() throws Exception {
        WEB_DRIVER_CONTAINER.start();
        HELM_CHART_CONTAINER.start();
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void shutdown() {
        HELM_CHART_CONTAINER.stop();
        WEB_DRIVER_CONTAINER.stop();
        NETWORK.close();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenControlCenterEnabled_thenAbleToLogin() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "control-center";
        HELM_CHART_CONTAINER.createNamespace(namespace);
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                namespace,
                "-f",
                "/files/control-center-test-values.yaml");

        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, customResourceName);

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                namespace,
                HIVEMQ_CC_SERVICE_NAME,
                HIVEMQ_CC_SERVICE_PORT)) {
            final var options = new ChromeOptions();
            options.setAcceptInsecureCerts(true);

            final var webDriver = new RemoteWebDriver(WEB_DRIVER_CONTAINER.getSeleniumAddress(), options, false);

            webDriver.get("http://host.docker.internal:" + forwarded.getLocalPort());
            final var wait = new WebDriverWait(webDriver, Duration.ofSeconds(30));

            wait.until(webWaitDriver -> {
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).sendKeys("admin");
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).sendKeys("hivemq");
                webWaitDriver.findElement(By.cssSelector(LOGIN_BUTTON)).click();
                return true;
            });

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(LOGOUT_BUTTON)));
            webDriver.quit();
        }

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, "--namespace", namespace);
        await().atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(K8sUtil.getHiveMQPlatform(client, namespace, customResourceName)
                        .get()).as("Unable to assert custom resource '%s' is uninstalled", customResourceName)
                        .isNull());
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenOverrideControlCenter_thenAbleToLogin() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "override-control-center";
        HELM_CHART_CONTAINER.createNamespace(namespace);
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                namespace,
                "-f",
                "/files/override-control-center-test-values.yaml");

        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, customResourceName);

        // forward the port from the service
        try (final var forwarded = K8sUtil.getPortForward(client,
                namespace,
                HIVEMQ_CC_SERVICE_NAME,
                HIVEMQ_CC_SERVICE_PORT)) {
            final var options = new ChromeOptions();
            options.setAcceptInsecureCerts(true);

            final var webDriver = new RemoteWebDriver(WEB_DRIVER_CONTAINER.getSeleniumAddress(), options, false);

            webDriver.get("http://host.docker.internal:" + forwarded.getLocalPort());
            final var wait = new WebDriverWait(webDriver, Duration.ofSeconds(30));

            wait.until(webWaitDriver -> {
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).sendKeys("test");
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).click();
                webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).sendKeys("abc123");
                webWaitDriver.findElement(By.cssSelector(LOGIN_BUTTON)).click();
                return true;
            });

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(LOGOUT_BUTTON)));
            webDriver.quit();
        }

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, "--namespace", namespace);
        await().atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(K8sUtil.getHiveMQPlatform(client, namespace, customResourceName)
                        .get()).as("Unable to assert custom resource '%s' is uninstalled", customResourceName)
                        .isNull());
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }
}
