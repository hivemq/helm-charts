package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmMqttIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT_1883 = 1883;
    private static final int MQTT_SERVICE_PORT_1884 = 1884;
    private static final @NotNull String MQTT_SERVICE_NAME =
            "hivemq-test-hivemq-platform-mqtt-" + MQTT_SERVICE_PORT_1884;
    private static final @NotNull String MQTT_CUSTOM_SERVICE_NAME = "mqtt-service";

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttEnabled_thenSendsReceivesMessage() {
        helmUpgradePlatform.withValuesFile(VALUES_PATH.resolve("mqtt-values.yaml")).call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        K8sUtil.assertMqttService(client, platformNamespace, MQTT_SERVICE_NAME);
        MqttUtil.assertMessages(client, platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttNodePortEnabled_thenSendsReceivesMessage() {
        helmUpgradePlatform.withValuesFile(VALUES_PATH.resolve("mqtt-node-port-values.yaml")).call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var services = client.services().inNamespace(platformNamespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("NodePort");
        });
        MqttUtil.assertMessages(client, platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttLoadBalancerEnabled_thenSendsReceivesMessage() {
        helmUpgradePlatform.withValuesFile(VALUES_PATH.resolve("mqtt-load-balancer-values.yaml")).call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var services = client.services().inNamespace(platformNamespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("LoadBalancer");
        });
        MqttUtil.assertMessages(client, platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttAnnotationsEnabled_thenAnnotationsExist() {
        helmUpgradePlatform.withValuesFile(VALUES_PATH.resolve("mqtt-annotations-values.yaml")).call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
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
        MqttUtil.assertMessages(client, platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT_1884);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_withCustomServiceName_thenSendsReceivesMessage() {
        helmUpgradePlatform.withValuesFile(VALUES_PATH.resolve("custom-service-names-values.yaml")).call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        K8sUtil.assertMqttService(client, platformNamespace, MQTT_CUSTOM_SERVICE_NAME);
        MqttUtil.assertMessages(client, platformNamespace, MQTT_CUSTOM_SERVICE_NAME, MQTT_SERVICE_PORT_1883);
    }
}
