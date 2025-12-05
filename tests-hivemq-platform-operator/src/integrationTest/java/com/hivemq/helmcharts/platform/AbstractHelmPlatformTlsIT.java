package com.hivemq.helmcharts.platform;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.selenium.BrowserWebDriverContainer;

import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AbstractHelmPlatformTlsIT extends AbstractHelmChartIT {

    static final @NotNull String MQTT_SERVICE_NAME_1884 = "hivemq-test-hivemq-platform-mqtt-1884";
    static final int MQTT_SERVICE_PORT_1884 = 1884;
    static final @NotNull String MQTT_SERVICE_NAME_1885 = "hivemq-test-hivemq-platform-mqtt-1885";
    static final int MQTT_SERVICE_PORT_1885 = 1885;
    static final @NotNull String MQTT_SERVICE_NAME_1886 = "hivemq-test-hivemq-platform-mqtt-1886";
    static final int MQTT_SERVICE_PORT_1886 = 1886;
    static final @NotNull String HIVEMQ_CC_SERVICE_NAME = "hivemq-test-hivemq-platform-cc-8080";
    static final int HIVEMQ_CC_SERVICE_PORT = 8080;

    @TempDir
    @NotNull Path tmp;

    @Container
    static final @NotNull BrowserWebDriverContainer WEB_DRIVER_CONTAINER =
            new BrowserWebDriverContainer(OciImages.getImageName("selenium/standalone-firefox")) //
                    .withNetwork(network) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway");

    @SuppressWarnings("SameParameterValue")
    void assertMqttListener(final @NotNull String serviceName, final int servicePort) {
        assertMqttListener(serviceName, servicePort, null);
    }

    void assertMqttListener(
            final @NotNull String serviceName,
            final int servicePort,
            final @Nullable MqttClientSslConfig sslConfig) {
        MqttUtil.assertMessages(client,
                platformNamespace,
                serviceName,
                servicePort,
                mqttClientModifier -> mqttClientModifier.sslConfig(sslConfig));
    }

    void assertSecretMounted(final @NotNull KubernetesClient client, final @NotNull String name) {
        final var statefulSet =
                client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
        assertThat(statefulSet).isNotNull();
        final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
        assertThat(volumes).isNotEmpty();

        final var tlsVolume = volumes.stream().filter(v -> Objects.equals(v.getName(), name)).findFirst();
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
