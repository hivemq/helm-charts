package com.hivemq.helmcharts.single;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.CertificatesUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hivemq.client.util.KeyStoreUtil.keyManagerFromKeystore;
import static com.hivemq.client.util.KeyStoreUtil.trustManagerFromKeystore;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_KEYSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.CertificatesUtil.DEFAULT_TRUSTSTORE_PASSWORD;
import static com.hivemq.helmcharts.util.K8sUtil.createSecret;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Platform")
@Tag("Tls")
@SuppressWarnings("DuplicatedCode")
class HelmPlatformMutualTlsIT extends AbstractHelmChartIT {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmPlatformMutualTlsIT.class);

    private static final int MQTT_SERVICE_PORT_1884 = 1884;
    private static final @NotNull String MQTT_SERVICE_NAME_1884 = "hivemq-test-hivemq-platform-mqtt-1884";
    private static final int MQTT_SERVICE_PORT_1885 = 1885;
    private static final @NotNull String MQTT_SERVICE_NAME_1885 = "hivemq-test-hivemq-platform-mqtt-1885";

    private @NotNull Path brokerCertificateStore;
    private @NotNull Path clientCertificateStore;

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setup(@TempDir final @NotNull Path tempDir) throws Exception {
        CertificatesUtil.generateCertificates(tempDir.toFile());
        final var encoder = Base64.getEncoder();

        brokerCertificateStore = tempDir.resolve("keystore.jks");
        final var keystoreContent = Files.readAllBytes(brokerCertificateStore);
        createSecret(client,
                platformNamespace,
                "mqtts-keystore-1884",
                "keystore",
                encoder.encodeToString(keystoreContent));
        createSecret(client,
                platformNamespace,
                "mqtts-keystore-1885",
                "keystore",
                encoder.encodeToString(keystoreContent));
        createSecret(client,
                platformNamespace,
                "mqtts-keystore-password-1885",
                "keystore.password",
                encoder.encodeToString(DEFAULT_KEYSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));

        clientCertificateStore = tempDir.resolve("truststore.jks");
        final var truststoreContent = Files.readAllBytes(clientCertificateStore);
        final var base64TruststoreContent = encoder.encodeToString(truststoreContent);
        createSecret(client, platformNamespace, "mqtts-truststore-1884", "truststore.jks", base64TruststoreContent);
        createSecret(client, platformNamespace, "mqtts-truststore-1885", "truststore", base64TruststoreContent);
        createSecret(client,
                platformNamespace,
                "mqtts-truststore-password-1885",
                "truststore.password",
                encoder.encodeToString(DEFAULT_TRUSTSTORE_PASSWORD.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withMutualTls_hivemqRunning() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mtls-mqtt-values.yaml");

        final var statefulSet =
                client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
        assertThat(statefulSet).isNotNull();

        LOG.info("Connecting to MQTT listener with no mTLS/SSL on port {}", DEFAULT_MQTT_SERVICE_PORT);
        assertMqttListener(DEFAULT_MQTT_SERVICE_NAME, DEFAULT_MQTT_SERVICE_PORT);

        final var sslConfig = MqttClientSslConfig.builder()
                .keyManagerFactory(keyManagerFromKeystore(brokerCertificateStore.toFile(),
                        DEFAULT_KEYSTORE_PASSWORD,
                        DEFAULT_KEYSTORE_PASSWORD))
                .trustManagerFactory(trustManagerFromKeystore(clientCertificateStore.toFile(),
                        DEFAULT_TRUSTSTORE_PASSWORD))
                .hostnameVerifier((hostname, session) -> true)
                .build();

        LOG.info("Connecting to MQTT listener mTLS/SSL on port {}", MQTT_SERVICE_PORT_1884);
        assertSecretMounted(statefulSet, "mqtts-keystore-1884");
        assertSecretMounted(statefulSet, "mqtts-truststore-1884");
        assertMqttListener(MQTT_SERVICE_NAME_1884, MQTT_SERVICE_PORT_1884, sslConfig);

        LOG.info("Connecting to MQTT listener mTLS/SSL on port {}", MQTT_SERVICE_PORT_1885);
        assertSecretMounted(statefulSet, "mqtts-keystore-1885");
        assertSecretMounted(statefulSet, "mqtts-truststore-1885");
        assertMqttListener(MQTT_SERVICE_NAME_1885, MQTT_SERVICE_PORT_1885, sslConfig);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertMqttListener(final @NotNull String serviceName, final int servicePort) {
        assertMqttListener(serviceName, servicePort, null);
    }

    private void assertMqttListener(
            final @NotNull String serviceName, final int servicePort, final @Nullable MqttClientSslConfig sslConfig) {
        MqttUtil.assertMessages(client,
                platformNamespace,
                serviceName,
                servicePort,
                clientBuilder -> clientBuilder.sslConfig(sslConfig));
    }

    private static void assertSecretMounted(StatefulSet statefulSet, final @NotNull String name) {
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
