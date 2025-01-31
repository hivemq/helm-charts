package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BrowserWebDriverContainer;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ControlCenterUtil {

    private static final int MAX_LOGIN_RETRIES = 5;
    private static final @NotNull String LOGIN_BUTTON = ".v-button-hmq-login-button";
    private static final @NotNull String LOGOUT_BUTTON = ".hmq-logout-button";
    private static final @NotNull String TEXT_INPUT_XPATH = "//input[@type='text']";
    private static final @NotNull String PASSWORD_INPUT_XPATH = "//input[@type='password']";

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(ControlCenterUtil.class);

    private ControlCenterUtil() {
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer<?> webDriverContainer,
            final @NotNull String serviceName,
            final int port) throws IOException {
        assertLogin(client, namespace, webDriverContainer, serviceName, port, "admin", "hivemq", false);
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer<?> webDriverContainer,
            final @NotNull String serviceName,
            final int port,
            final boolean isSecure) throws IOException {
        assertLogin(client, namespace, webDriverContainer, serviceName, port, "admin", "hivemq", isSecure);
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer<?> webDriverContainer,
            final @NotNull String serviceName,
            final int port,
            final @NotNull String username,
            final @NotNull String password) throws IOException {
        assertLogin(client, namespace, webDriverContainer, serviceName, port, username, password, false);
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer<?> webDriverContainer,
            final @NotNull String serviceName,
            final int port,
            final @NotNull String username,
            final @NotNull String password,
            final boolean isSecure) throws IOException {
        LOG.info("Log into CC on {}:{} (username: {}) (password: {}) (TLS: {})",
                serviceName,
                port,
                username,
                password,
                isSecure);
        final var loginRetry = new AtomicInteger();
        while (true) {
            final var loginAttempt = loginRetry.incrementAndGet();
            try (final var forwarded = K8sUtil.getPortForward(client, namespace, serviceName, port)) {
                final var options = new ChromeOptions();
                options.setAcceptInsecureCerts(true);

                final var webDriver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), options, false);
                webDriver.get(String.format("%s://host.docker.internal:%s",
                        isSecure ? "https" : "http",
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
