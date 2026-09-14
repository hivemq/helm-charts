package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.By;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.selenium.BrowserWebDriverContainer;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ControlCenterUtil {

    private static final int MAX_LOGIN_RETRIES = 5;
    private static final @NotNull Duration FORM_READY_TIMEOUT = Duration.ofSeconds(30);
    private static final @NotNull Duration LOGIN_SUCCESS_TIMEOUT = Duration.ofSeconds(30);
    private static final @NotNull By LOGIN_FORM = By.cssSelector("[data-testid='login-form']");
    private static final @NotNull By USERNAME_INPUT = By.cssSelector("[data-testid='username']");
    private static final @NotNull By PASSWORD_INPUT = By.cssSelector("[data-testid='password']");
    private static final @NotNull By LOGIN_BUTTON = By.cssSelector("[data-testid='login-button']");
    private static final @NotNull By USER_MENU_BUTTON = By.cssSelector("[data-testid='ccv2-user-menu-button']");

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(ControlCenterUtil.class);

    private ControlCenterUtil() {
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer webDriverContainer,
            final @NotNull String serviceName,
            final int port) throws IOException {
        assertLogin(client, namespace, webDriverContainer, serviceName, port, "admin", "hivemq", false);
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer webDriverContainer,
            final @NotNull String serviceName,
            final int port,
            final boolean isSecure) throws IOException {
        assertLogin(client, namespace, webDriverContainer, serviceName, port, "admin", "hivemq", isSecure);
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer webDriverContainer,
            final @NotNull String serviceName,
            final int port,
            final @NotNull String username,
            final @NotNull String password) throws IOException {
        assertLogin(client, namespace, webDriverContainer, serviceName, port, username, password, false);
    }

    public static void assertLogin(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull BrowserWebDriverContainer webDriverContainer,
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
                final var options = new FirefoxOptions();
                options.setAcceptInsecureCerts(true);

                final var webDriver = new RemoteWebDriver(webDriverContainer.getSeleniumAddress(), options, false);
                webDriver.get("%s://host.docker.internal:%s".formatted(isSecure ? "https" : "http",
                        forwarded.getLocalPort()));

                try {
                    final var formWait = new WebDriverWait(webDriver, FORM_READY_TIMEOUT);
                    formWait.until(ExpectedConditions.visibilityOfElementLocated(LOGIN_FORM));

                    webDriver.findElement(USERNAME_INPUT).click();
                    webDriver.findElement(USERNAME_INPUT).sendKeys(username);
                    webDriver.findElement(PASSWORD_INPUT).click();
                    webDriver.findElement(PASSWORD_INPUT).sendKeys(password);
                    webDriver.findElement(LOGIN_BUTTON).click();

                    final var loggedInWait = new WebDriverWait(webDriver, LOGIN_SUCCESS_TIMEOUT);
                    loggedInWait.until(ExpectedConditions.visibilityOfElementLocated(USER_MENU_BUTTON));
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
