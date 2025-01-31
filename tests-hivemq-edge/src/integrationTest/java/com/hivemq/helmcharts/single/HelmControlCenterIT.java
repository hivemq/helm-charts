package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import org.jetbrains.annotations.NotNull;
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
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.SELENIUM_DOCKER_IMAGE;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.ENV_VAR_PRIVATE_KEY_PASSWORD;
import static com.hivemq.helmcharts.util.ControlCenterUtil.assertLogin;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;

@Tag("Services")
@Tag("Services2")
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
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
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

        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
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

        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8081, CC_SERVICE_PORT_8081);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8443, CC_SERVICE_PORT_8443, true);
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_SERVICE_NAME_8444, CC_SERVICE_PORT_8444, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenOverrideControlCenter_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/override-control-center-values.yaml");
        assertLogin(client,
                platformNamespace,
                WEB_DRIVER_CONTAINER,
                CC_SERVICE_NAME_8081,
                CC_SERVICE_PORT_8081,
                "test",
                "abc123");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenAbleToLogin() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");
        assertLogin(client, platformNamespace, WEB_DRIVER_CONTAINER, CC_CUSTOM_SERVICE_NAME, CC_SERVICE_PORT_8081);
    }
}
