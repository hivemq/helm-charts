package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.RestAPIUtil;
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

class HelmRestApiTlsIT extends AbstractHelmChartIT {

    private static final int REST_API_SERVICE_PORT_8890 = 8890;
    private static final int REST_API_SERVICE_PORT_8891 = 8891;
    private static final @NotNull String REST_API_SERVICE_NAME_8890 =
            "hivemq-test-hivemq-platform-rest-" + REST_API_SERVICE_PORT_8890;
    private static final @NotNull String REST_API_SERVICE_NAME_8891 =
            "hivemq-test-hivemq-platform-rest-" + REST_API_SERVICE_PORT_8891;

    @TempDir
    private @NotNull Path tmp;

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

        assertThat(RestAPIUtil.getAllMqttClients(client,
                platformNamespace,
                REST_API_SERVICE_NAME_8890,
                REST_API_SERVICE_PORT_8890,
                true)).isEmpty();
        assertThat(RestAPIUtil.getAllMqttClients(client,
                platformNamespace,
                REST_API_SERVICE_NAME_8891,
                REST_API_SERVICE_PORT_8891,
                true)).isEmpty();
    }
}
