package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("Extensions")
@Testcontainers
class EseExtensionInstallationIT {

    private static final @NotNull String CHART_NAME = "local-hivemq";

    @Container
    private final @NotNull OperatorHelmChartContainer container =
            new OperatorHelmChartContainer(DockerImageNames.K3s.DEFAULT,
                    "values/test-values.yaml",
                    CHART_NAME).withLocalImages();

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void withExtensionConfiguration_hivemqRunning() throws Exception {
        final var client = container.getKubernetesClient();
        final var namespace = "default";

        final var resource = getClass().getResource("/ese-config.xml");

        final var configMapData = Files.readString(Path.of(Objects.requireNonNull(resource).toURI()));
        final var eseConfigMap = new ConfigMapBuilder().withNewMetadata()
                .withName("ese-extension-config")
                .endMetadata()
                .withData(Map.of("hivemq-enterprise-security-extension.xml", configMapData))
                .build();
        client.resource(eseConfigMap).inNamespace(namespace).create();

        container.upgradeLocalChart(CHART_NAME, "/values/ese-extension-values.yaml");

        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Updating");
        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Running");

        final var deployment = client.apps().deployments().inNamespace(namespace).withName("local-hivemq").get();
        assertThat(deployment).isNotNull();
        final var container = deployment.getSpec().getTemplate().getSpec().getContainers().getFirst();
        assertThat(container).isNotNull();
        assertThat(container.getName()).isEqualTo("hivemq");
        final var foundMount = container.getVolumeMounts()
                .stream()
                .filter(v -> v.getName().contains("ese-extension-config"))
                .findFirst();
        assertThat(foundMount).isPresent().hasValueSatisfying(volumeMount -> assertThat(volumeMount.getMountPath()) //
                .isEqualTo("/conf-override/extensions/hivemq-enterprise-security-extension"));
    }
}
