package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

@Tag("Services")
@Tag("Services1")
class HelmMqttIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT_1883 = 1883;
    private static final int MQTT_SERVICE_PORT_1884 = 1884;
    private static final @NotNull String MQTT_SERVICE_NAME =
            "hivemq-test-hivemq-platform-mqtt-" + MQTT_SERVICE_PORT_1884;
    private static final @NotNull String MQTT_CUSTOM_SERVICE_NAME = "mqtt-service";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttEnabled_thenSendsReceivesMessage() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mqtt-values.yaml");
        K8sUtil.assertMqttService(client, platformNamespace, MQTT_SERVICE_NAME);
        assertMqttListener(platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttNodePortEnabled_thenSendsReceivesMessage() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mqtt-node-port-values.yaml");
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var services = client.services().inNamespace(platformNamespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("NodePort");
        });
        assertMqttListener(platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttLoadBalancerEnabled_thenSendsReceivesMessage() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mqtt-load-balancer-values.yaml");
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var services = client.services().inNamespace(platformNamespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("LoadBalancer");
        });
        assertMqttListener(platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttAnnotationsEnabled_thenAnnotationsExist() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mqtt-annotations-values.yaml");
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var services = client.services().inNamespace(platformNamespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .allSatisfy(service -> assertThat(service.getMetadata()
                            .getAnnotations()).containsAllEntriesOf(Map.of("test-annotation-key",
                            "test-annotation-value",
                            "test-annotation-key/v1",
                            "test-annotation-value-v1")));
        });
        assertMqttListener(platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenSendsReceivesMessage() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/custom-service-names-values.yaml");
        K8sUtil.assertMqttService(client, platformNamespace, MQTT_CUSTOM_SERVICE_NAME);
        assertMqttListener(platformNamespace, MQTT_CUSTOM_SERVICE_NAME, MQTT_SERVICE_PORT_1883);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertMqttListener(
            final @NotNull String namespace, final @NotNull String serviceName, final int servicePort) {
        MqttUtil.assertMessages(client, namespace, serviceName, servicePort);
    }
}
