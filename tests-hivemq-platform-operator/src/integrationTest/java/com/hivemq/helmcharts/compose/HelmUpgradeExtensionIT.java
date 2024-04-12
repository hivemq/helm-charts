package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.event.Level;
import org.testcontainers.containers.Network;
import org.testcontainers.hivemq.HiveMQContainer;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.HIVEMQ_DOCKER_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("Extensions")
@SuppressWarnings("DuplicatedCode")
class HelmUpgradeExtensionIT {

    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";

    private static final @NotNull Network NETWORK = Network.newNetwork();
    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER =
            new HelmChartContainer().withNetwork(NETWORK);
    private static final @NotNull HiveMQContainer HIVEMQ_CONTAINER =
            new HiveMQContainer(HIVEMQ_DOCKER_IMAGE).withLogLevel(Level.DEBUG)
                    .withNetwork(NETWORK)
                    .withNetworkAliases("remote");

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void setup() throws Exception {
        HIVEMQ_CONTAINER.start();
        HELM_CHART_CONTAINER.start();
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void shutdown() {
        HELM_CHART_CONTAINER.stop();
        HIVEMQ_CONTAINER.stop();
        NETWORK.close();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeConfiguration_enableDisableBridged() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "enable-disable-extension-test";
        HELM_CHART_CONTAINER.createNamespace(namespace);

        final var client = HELM_CHART_CONTAINER.getKubernetesClient();
        final var hivemqContainerNetwork =
                HIVEMQ_CONTAINER.getContainerInfo().getNetworkSettings().getNetworks().values().stream().findFirst();
        assertThat(hivemqContainerNetwork).isPresent();

        // setup bridge configuration
        final URL resource = getClass().getResource("/bridge-config.xml");
        final var configMapData = Files.readString(Path.of(Objects.requireNonNull(resource).toURI()));
        final var bridgeConfigMap = new ConfigMapBuilder().withNewMetadata()
                .withName("test-bridge-configuration")
                .endMetadata()
                .withData(Map.of("config.xml",
                        configMapData.replace("<host>remote</host>",
                                "<host>" + hivemqContainerNetwork.get().getIpAddress() + "</host>")))
                .build();
        client.configMaps().inNamespace(namespace).resource(bridgeConfigMap).create();

        // deploy chart and wait to be ready
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/bridge-test-values.yaml",
                "--namespace",
                namespace);

        final Resource<GenericKubernetesResource> hivemqCustomResource =
                K8sUtil.getHiveMQPlatform(client, namespace, customResourceName);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 3, TimeUnit.MINUTES);
        // check that extensions are enabled
        assertThat(hivemqCustomResource.get().getAdditionalProperties().get("spec").toString()).matches(
                ".*extensions=\\[.*?enabled=true,.*?id=hivemq-bridge-extension,.*?].*");

        // upgrade chart and wait to be ready
        HELM_CHART_CONTAINER.upgradePlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/disable-bridge-test-values.yaml",
                "--namespace",
                namespace);

        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RESTART_EXTENSIONS"),
                1,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 3, TimeUnit.MINUTES);
        // check that extensions are disabled
        assertThat(hivemqCustomResource.get().getAdditionalProperties().get("spec").toString()).matches(
                ".*extensions=\\[.*?enabled=false,.*?id=hivemq-bridge-extension,.*?].*");

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeConfiguration_updateExtensionWithNewConfig() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "update-extension-new-config";
        HELM_CHART_CONTAINER.createNamespace(namespace);

        final var client = HELM_CHART_CONTAINER.getKubernetesClient();
        final var hivemqContainerNetwork =
                HIVEMQ_CONTAINER.getContainerInfo().getNetworkSettings().getNetworks().values().stream().findFirst();
        assertThat(hivemqContainerNetwork).isPresent();

        // setup bridge configuration
        final URL resource = getClass().getResource("/bridge-config.xml");
        final var configMapData = Files.readString(Path.of(Objects.requireNonNull(resource).toURI()));
        final var bridgeConfigMap = new ConfigMapBuilder().withNewMetadata()
                .withName("test-bridge-configuration")
                .endMetadata()
                .withData(Map.of("config.xml",
                        configMapData.replace("<host>remote</host>",
                                "<host>" + hivemqContainerNetwork.get().getIpAddress() + "</host>")))
                .build();
        client.configMaps().inNamespace(namespace).resource(bridgeConfigMap).create();

        // deploy chart and wait to be ready
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/bridge-test-values.yaml",
                "--namespace",
                namespace);
        final Resource<GenericKubernetesResource> hivemqCustomResource =
                K8sUtil.getHiveMQPlatform(client, namespace, customResourceName);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 3, TimeUnit.MINUTES);

        // check that extensions are enabled
        assertThat(hivemqCustomResource.get().getAdditionalProperties().get("spec").toString()).matches(
                ".*extensions=\\[.*?enabled=true,.*?id=hivemq-bridge-extension,.*?].*");

        // create a new config map with a different name
        final var newBridgeConfigMap = new ConfigMapBuilder().withNewMetadata()
                .withName("new-test-bridge-configuration")
                .endMetadata()
                .withData(Map.of("config.xml",
                        configMapData.replace("<host>remote</host>",
                                "<host>" + hivemqContainerNetwork.get().getIpAddress() + "</host>")))
                .build();
        client.configMaps().inNamespace(namespace).resource(newBridgeConfigMap).create();

        // upgrade chart and wait to be ready
        HELM_CHART_CONTAINER.upgradePlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/bridge-test-updated-values.yaml",
                "--namespace",
                namespace);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("ROLLING_RESTART"),
                3,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 3, TimeUnit.MINUTES);

        final StatefulSet upgradedStatefulSet =
                client.apps().statefulSets().inNamespace(namespace).withName(customResourceName).get();
        assertThat(upgradedStatefulSet.getStatus().getAvailableReplicas()).isEqualTo(1);

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }
}
