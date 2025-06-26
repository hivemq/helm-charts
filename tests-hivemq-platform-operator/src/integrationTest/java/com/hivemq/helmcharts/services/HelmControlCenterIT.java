package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

@Testcontainers
class HelmControlCenterIT extends AbstractHelmChartIT {

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

    @TempDir
    private @NotNull Path tmp;

    @Container
    @SuppressWarnings("resource")
    private static final @NotNull BrowserWebDriverContainer<?> WEB_DRIVER_CONTAINER =
            new BrowserWebDriverContainer<>(OciImages.getImageName("selenium/standalone-firefox")) //
                    .withNetwork(network) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway") //
                    .withCapabilities(new FirefoxOptions());

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenControlCenterEnabled_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/control-center-values.yaml");
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabled_thenAbleToLogin() throws Exception {
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

        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenTlsEnabledWithDifferentPrivateKey_thenAbleToLogin() throws Exception {
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

        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomControlCenterUsernameAndPassword_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/control-center-basic-credentials-values.yaml");
        assertLogin(client,
                platformNamespace,
                WEB_DRIVER_CONTAINER,
                CC_SERVICE_NAME_8081,
                CC_SERVICE_PORT_8081,
                "test-username",
                "test-password");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomControlCenterCredentialsSecret_thenAbleToLogin() throws Exception {
        final var encoder = Base64.getEncoder();
        createSecret(client,
                platformNamespace,
                "control-center-credentials",
                Map.of("username-secret-key",
                        encoder.encodeToString("test-username".getBytes(StandardCharsets.UTF_8)),
                        "password-secret-key",
                        // SHA256 of test-usernametest-password
                        encoder.encodeToString("6300801c77089c00a55fd57002e856c6934d3764a1e50cc59e8c99f47e8e10a7".getBytes(
                                StandardCharsets.UTF_8))));
        installPlatformChartAndWaitToBeRunning("/files/control-center-credentials-secret-values.yaml");
        assertLogin(client,
                platformNamespace,
                WEB_DRIVER_CONTAINER,
                CC_SERVICE_NAME_8081,
                CC_SERVICE_PORT_8081,
                "test-username",
                "test-password");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_CUSTOM_SERVICE_NAME, CC_SERVICE_PORT_8081);
    }
}
