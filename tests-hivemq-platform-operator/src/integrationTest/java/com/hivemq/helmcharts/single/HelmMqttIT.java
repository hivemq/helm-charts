package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Services")
@Tag("Services1")
class HelmMqttIT {

    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    private static final @NotNull String NAMESPACE = K8sUtil.getNamespaceName(HelmMqttIT.class);
    private static final @NotNull String MQTT_SERVICE_NAME = "hivemq-test-hivemq-platform-mqtt-1884";
    private static final int MQTT_SERVICE_PORT = 1884;

    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseSetup() {
        HELM_CHART_CONTAINER.start();
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseTearDown() {
        HELM_CHART_CONTAINER.stop();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setup() throws Exception {
        HELM_CHART_CONTAINER.createNamespace(NAMESPACE);
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, NAMESPACE, true);
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME, "default");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttEnabled_thenSendsReceivesMessage() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/mqtt-test-values.yaml");

        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);
        K8sUtil.assertMqttService(client, NAMESPACE, MQTT_SERVICE_NAME);
        assertMqttListener(NAMESPACE, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttNodePortEnabled_thenSendsReceivesMessage() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/mqtt-node-port-test-values.yaml");

        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);
        await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
            final var services = client.services().inNamespace(NAMESPACE).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("NodePort");
        });
        assertMqttListener(NAMESPACE, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttLoadBalancerEnabled_thenSendsReceivesMessage() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/mqtt-load-balancer-test-values.yaml");

        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);
        await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
            final var services = client.services().inNamespace(NAMESPACE).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("LoadBalancer");
        });
        assertMqttListener(NAMESPACE, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenMqttAnnotationsEnabled_thenAnnotationsExist() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "-f",
                "/files/mqtt-annotations-test-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            final var services = client.services().inNamespace(NAMESPACE).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .allSatisfy(service -> assertThat(service.getMetadata()
                            .getAnnotations()).containsAllEntriesOf(Map.of("test-annotation-key",
                            "test-annotation-value",
                            "test-annotation-key/v1",
                            "test-annotation-value-v1")));
        });
        assertMqttListener(NAMESPACE, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertMqttListener(
            final @NotNull String namespace, final @NotNull String serviceName, final int servicePort) {
        MqttUtil.execute(client, namespace, serviceName, servicePort, MqttUtil.withDefaultPublishSubscribeRunnable());
    }
}
