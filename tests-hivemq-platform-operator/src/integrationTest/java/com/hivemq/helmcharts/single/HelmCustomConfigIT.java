package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("CustomConfig")
class HelmCustomConfigIT {

    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void setup() throws Exception {
        HELM_CHART_CONTAINER.start();
        HELM_CHART_CONTAINER.installOperatorChart("test-hivemq-platform-operator");
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void shutdown() {
        HELM_CHART_CONTAINER.stop();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomYml_hivemqRunning() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "config-custom-yml";
        HELM_CHART_CONTAINER.createNamespace(namespace);
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                namespace,
                "--set-file",
                "config.overrideStatefulSet=/files/stateful-set-spec.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, customResourceName);

        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(namespace).withName(customResourceName).get();
            assertThat(statefulSet).isNotNull();
            final var foundContainer = statefulSet.getSpec()
                    .getTemplate()
                    .getSpec()
                    .getContainers()
                    .stream()
                    .filter(c -> c.getName().equalsIgnoreCase("hivemq"))
                    .findFirst();
            assertThat(foundContainer).isPresent();
            assertThat(foundContainer.get() //
                    .getPorts() //
                    .stream() //
                    .filter(p -> p.getName().startsWith("mqtt"))) //
                    .anyMatch(p -> p.getContainerPort().equals(1884));
        });

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomXml_hivemqRunning() throws Exception {
        final var namespace = "config-custom-xml";
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.createNamespace(namespace);
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                namespace,
                "--set-file",
                "config.overrideHiveMQConfig=/files/hivemq-config-override.xml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, customResourceName);

        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            final var configmap = client.configMaps()
                    .inNamespace(namespace)
                    .withName("hivemq-configuration-test-hivemq-platform")
                    .get();
            assertThat(configmap).isNotNull();
            final var xmlConfig = configmap.getData().get("config.xml");
            assertThat(xmlConfig).isNotNull().contains("<port>1884</port>");
        });

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingConfigMap_customResourceCreated() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "existing-configmap";
        HELM_CHART_CONTAINER.createNamespace(namespace);

        final var configMap = K8sUtil.createConfigMap(client, namespace, "hivemq-config-map.yml");

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                namespace,
                "--set",
                "config.create=false",
                "--set",
                "config.name=" + configMap.getMetadata().getName());
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, customResourceName);

        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, namespace, customResourceName).get();
            assertThat(hivemqCustomResource.getAdditionalProperties().get("spec")).isNotNull()
                    .asString()
                    .containsIgnoringCase("configMapName=hivemq-configuration");
        });

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }
}
