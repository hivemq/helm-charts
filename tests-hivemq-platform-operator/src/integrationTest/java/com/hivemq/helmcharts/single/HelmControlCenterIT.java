package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.SELENIUM_DOCKER_IMAGE;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

@Tag("Services")
@Tag("Services2")
@Testcontainers
class HelmControlCenterIT extends AbstractHelmChartIT {

    private static final int MAX_LOGIN_RETRIES = 5;
    private static final int CC_SERVICE_PORT_8081 = 8081;
    private static final int CC_SERVICE_PORT_8443 = 8443;
    private static final int CC_SERVICE_PORT_8444 = 8444;
    private static final @NotNull String CC_SERVICE_NAME_8081 =
            "hivemq-test-hivemq-platform-cc-" + CC_SERVICE_PORT_8081;
    private static final @NotNull String CC_SERVICE_NAME_8443 =
            "hivemq-test-hivemq-platform-cc-" + CC_SERVICE_PORT_8443;
    private static final @NotNull String CC_SERVICE_NAME_8444 =
            "hivemq-test-hivemq-platform-cc-" + CC_SERVICE_PORT_8444;
    private static final @NotNull String CC_CUSTOM_SERVICE_NAME = "control-center-service";
    private static final @NotNull String LOGIN_BUTTON = ".v-button-hmq-login-button";
    private static final @NotNull String LOGOUT_BUTTON = ".hmq-logout-button";
    private static final @NotNull String TEXT_INPUT_XPATH = "//input[@type='text']";
    private static final @NotNull String PASSWORD_INPUT_XPATH = "//input[@type='password']";

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmControlCenterIT.class);

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
    void platformChart_whenControlCenterEnabled_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/control-center-values.yaml");
        assertControlCenterListener(CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081, false);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabled_thenAbleToLogin(@TempDir final @NotNull Path tmp) throws Exception {
        CertificatesUtil.generateCertificates(tmp.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);

        createSecret(client,
                platformNamespace,
                "cc-keystore-8443",
                "keystore.jks",
                encoder.encodeToString(keystoreContent));
        createSecret(client,
                platformNamespace,
                "cc-keystore-password-8443",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));
        createSecret(client,
                platformNamespace,
                "cc-keystore-8444",
                "keystore",
                encoder.encodeToString(keystoreContent));

        installPlatformChartAndWaitToBeRunning("/files/tls-cc-values.yaml");

        assertControlCenterListener(CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081, false);
        assertControlCenterListener(CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertControlCenterListener(CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabledWithDifferentPrivateKey_thenAbleToLogin(@TempDir final @NotNull Path tmp)
            throws Exception {
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

        assertControlCenterListener(CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081, false);
        assertControlCenterListener(CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertControlCenterListener(CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenOverrideControlCenter_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/override-control-center-values.yaml");
        assertControlCenterListener(CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081, "test", "abc123", false);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");
        assertControlCenterListener(CC_CUSTOM_SERVICE_NAME, CC_SERVICE_PORT_8081, false);
    }

    private void assertControlCenterListener(
            final @NotNull String serviceName, final int port, final boolean isTlsEnabled) throws Exception {
        assertControlCenterListener(serviceName, port, "admin", "hivemq", isTlsEnabled);
    }

    private void assertControlCenterListener(
            final @NotNull String serviceName,
            final int port,
            final @NotNull String username,
            final @NotNull String password,
            final boolean isTlsEnabled) throws Exception {
        LOG.info("Log into CC on {}:{} (username: {}) (password: {}) (TLS: {})",
                serviceName,
                port,
                username,
                password,
                isTlsEnabled);
        final var loginRetry = new AtomicInteger();
        while (true) {
            final var loginAttempt = loginRetry.incrementAndGet();
            // forward the port from the service
            try (final var forwarded = K8sUtil.getPortForward(client, platformNamespace, serviceName, port)) {
                final var options = new ChromeOptions();
                options.setAcceptInsecureCerts(true);

                final var webDriver = new RemoteWebDriver(WEB_DRIVER_CONTAINER.getSeleniumAddress(), options, false);
                webDriver.get(String.format("%s://host.docker.internal:%s",
                        isTlsEnabled ? "https" : "http",
                        forwarded.getLocalPort()));

                final var wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
                try {
                    wait.until(webWaitDriver -> {
                        webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).click();
                        webWaitDriver.findElement(By.xpath(TEXT_INPUT_XPATH)).sendKeys(username);
                        webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).click();
                        webWaitDriver.findElement(By.xpath(PASSWORD_INPUT_XPATH)).sendKeys(password);
                        webWaitDriver.findElement(By.cssSelector(LOGIN_BUTTON)).click();
                        return ExpectedConditions.visibilityOfElementLocated(By.cssSelector(LOGOUT_BUTTON));
                    });
                } catch (final Exception e) {
                    if (loginAttempt == MAX_LOGIN_RETRIES) {
                        LOG.error("Login attempt {} of {} failed, giving up", loginAttempt, MAX_LOGIN_RETRIES);
                        LOG.info("Source:\n{}", webDriver.getPageSource());
                        throw e;
                    }
                    LOG.warn("Login attempt {} of {} failed, will retry", loginAttempt, MAX_LOGIN_RETRIES);
                    continue;
                } finally {
                    webDriver.quit();
                }
            }
            LOG.info("Login attempt {} was successful", loginAttempt);
            break;
        }
    }
}
