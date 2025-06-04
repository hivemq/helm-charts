package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.RestAPIUtil;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

class HelmRestApiIT extends AbstractHelmChartIT {

    private static final int REST_API_SERVICE_PORT_8890 = 8890;
    private static final int REST_API_SERVICE_PORT_8891 = 8891;
    private static final @NotNull String REST_API_SERVICE_NAME_8890 =
            "hivemq-test-hivemq-platform-rest-" + REST_API_SERVICE_PORT_8890;
    private static final @NotNull String REST_API_SERVICE_NAME_8891 =
            "hivemq-test-hivemq-platform-rest-" + REST_API_SERVICE_PORT_8891;
    private static final @NotNull String REST_API_CUSTOM_SERVICE_NAME = "rest-api-service";

    @TempDir
    private @NotNull Path tmp;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenRestApiEnabled_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/rest-api-values.yaml");

        assertMqttClientsIsEmpty(REST_API_SERVICE_NAME_8890, REST_API_SERVICE_PORT_8890, false);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenAuthEnabled_thenCallsEndpoint() throws Exception {
        CertificatesUtil.generateCertificates(tmp.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);
        K8sUtil.createSecret(client,
                platformNamespace,
                "rest-api-keystore",
                Map.of("keystore.jks", base64KeystoreContent));
        K8sUtil.createSecret(client,
                platformNamespace,
                "rest-api-keystore-password",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        K8sUtil.createConfigMap(client, platformNamespace, "ese-config-map.yml");
        K8sUtil.createConfigMap(client, platformNamespace, "ese-file-realm-config-map.yml");

        installPlatformChartAndWaitToBeRunning("/files/rest-api-with-auth-values.yaml");

        RestAPIUtil.assertAuth(client, platformNamespace, REST_API_SERVICE_NAME_8890, REST_API_SERVICE_PORT_8890, true);
        RestAPIUtil.assertAuth(client,
                platformNamespace,
                REST_API_SERVICE_NAME_8890,
                REST_API_SERVICE_PORT_8890,
                true,
                "user",
                "wrong-password",
                HttpStatus.SC_UNAUTHORIZED);
        RestAPIUtil.assertAuth(client,
                platformNamespace,
                REST_API_SERVICE_NAME_8890,
                REST_API_SERVICE_PORT_8890,
                true,
                "/api/v1/management/backups",
                HttpStatus.SC_FORBIDDEN);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenCallsEndpoint() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");

        assertMqttClientsIsEmpty(REST_API_CUSTOM_SERVICE_NAME, REST_API_SERVICE_PORT_8890, false);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withTlsEnabled_thenCallsEndpoint() throws Exception {
        CertificatesUtil.generateCertificates(tmp.toFile());
        final var encoder = Base64.getEncoder();
        final var keystore = tmp.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(keystore);
        final var base64KeystoreContent = encoder.encodeToString(keystoreContent);
        K8sUtil.createSecret(client,
                platformNamespace,
                "rest-api-keystore",
                Map.of("keystore", base64KeystoreContent, "keystore.jks", base64KeystoreContent));
        K8sUtil.createSecret(client,
                platformNamespace,
                "rest-api-keystore-password",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        installPlatformChartAndWaitToBeRunning("/files/tls-rest-api-values.yaml");

        assertMqttClientsIsEmpty(REST_API_SERVICE_NAME_8890, REST_API_SERVICE_PORT_8890, true);
        assertMqttClientsIsEmpty(REST_API_SERVICE_NAME_8891, REST_API_SERVICE_PORT_8891, true);
    }

    private void assertMqttClientsIsEmpty(final @NotNull String serviceName, final int port, final boolean isSecure)
            throws Exception {
        final var clientList = RestAPIUtil.getAllMqttClients(client, platformNamespace, serviceName, port, isSecure);
        assertThat(clientList).isEmpty();
    }
}
