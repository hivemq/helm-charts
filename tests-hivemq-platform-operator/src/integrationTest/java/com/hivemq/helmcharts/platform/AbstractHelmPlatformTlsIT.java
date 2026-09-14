package com.hivemq.helmcharts.platform;

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.testcontainer.WebDriverContainerExtension;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.selenium.BrowserWebDriverContainer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NotNullFieldNotInitialized")
class AbstractHelmPlatformTlsIT extends AbstractHelmChartIT {

    @RegisterExtension
    private static final @NotNull WebDriverContainerExtension WEB_DRIVER_CONTAINER_EXTENSION =
            new WebDriverContainerExtension(network);

    static final int MQTT_SERVICE_PORT_1884 = 1884;
    static final int MQTT_SERVICE_PORT_1885 = 1885;
    static final int MQTT_SERVICE_PORT_1886 = 1886;
    static final int HIVEMQ_CC_SERVICE_PORT = 8080;

    final @NotNull String mqttServiceName1884 =
            "hivemq-%s-mqtt-%s".formatted(platformReleaseName, MQTT_SERVICE_PORT_1884);
    final @NotNull String mqttServiceName1885 =
            "hivemq-%s-mqtt-%s".formatted(platformReleaseName, MQTT_SERVICE_PORT_1885);
    final @NotNull String mqttServiceName1886 =
            "hivemq-%s-mqtt-%s".formatted(platformReleaseName, MQTT_SERVICE_PORT_1886);
    final @NotNull String hivemqCcServiceName =
            "hivemq-%s-cc-%s".formatted(platformReleaseName, HIVEMQ_CC_SERVICE_PORT);

    @TempDir
    @NotNull Path tmp;

    static @NotNull BrowserWebDriverContainer webDriverContainer;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void setupWebDriverContainer() {
        webDriverContainer = WEB_DRIVER_CONTAINER_EXTENSION.getWebDriverContainer();
    }

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
                client.apps().statefulSets().inNamespace(platformNamespace).withName(platformReleaseName).get();
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
