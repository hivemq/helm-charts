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

    private static final @NotNull String PLATFORM_RELEASE_NAME = "hivemq-platform";
    private static final @NotNull String OPERATOR_RELEASE_NAME = "platform-operator";
    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void setup() {
        HELM_CHART_CONTAINER.start();
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void shutdown() {
        HELM_CHART_CONTAINER.stop();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomYml_hivemqRunning() throws Exception {
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);

        final var customResourceName = "hivemq-platform";
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

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME, "default");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomXml_hivemqRunning() throws Exception {
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);

        final var namespace = "config-custom-xml";
        final var customResourceName = "hivemq-platform";
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
                    .withName("hivemq-configuration-hivemq-platform")
                    .get();
            assertThat(configmap).isNotNull();
            final var xmlConfig = configmap.getData().get("config.xml");
            assertThat(xmlConfig).isNotNull().contains("<port>1884</port>");
        });

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME, "default");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingConfigMap_customResourceCreated() throws Exception {
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);

        final var customResourceName = "hivemq-platform";
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

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME, "default");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomEnvVars_hivemqRunning() throws Exception {
        final var operatorNamespace = "operator-custom-env-vars";
        HELM_CHART_CONTAINER.createNamespace(operatorNamespace);
        final var configMap =
                K8sUtil.createConfigMap(client, operatorNamespace, "operator-custom-env-var-config-map.yml");
        assertThat(configMap).isNotNull();
        final var platformNamespace = "platform-custom-env-vars-namespace";
        final var operatorStartedFuture = HELM_CHART_CONTAINER.getLogWaiter()
                .waitFor("hivemq-platform-operator-.*",
                        ".*Registered reconciler: 'hivemq-controller' for resource: 'class com.hivemq.platform.operator.v1.HiveMQPlatform' for namespace\\(s\\): \\[platform-custom-env-vars-namespace\\]");
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME,
                "--namespace",
                operatorNamespace,
                "-f",
                "/files/custom-operator-env-vars-test-values.yaml");
        await().atMost(1, TimeUnit.MINUTES).until(operatorStartedFuture::isDone);

        final var customResourceName = "hivemq-platform";
        HELM_CHART_CONTAINER.createNamespace(platformNamespace);

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                platformNamespace,
                "-f",
                "/files/custom-platform-env-vars-test-values.yaml");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, customResourceName);

        final var statefulSet = K8sUtil.getStatefulSet(client, platformNamespace, customResourceName);
        assertThat(K8sUtil.getHiveMQContainer(statefulSet.getSpec())
                .getEnv()).anyMatch(envVar -> "MY_CUSTOM_ENV_VAR".equals(envVar.getName()) &&
                "mycustomvalue".equals(envVar.getValue()));

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, platformNamespace);
        HELM_CHART_CONTAINER.deleteNamespace(platformNamespace);
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME, operatorNamespace);
        HELM_CHART_CONTAINER.deleteNamespace(operatorNamespace);
    }
}
