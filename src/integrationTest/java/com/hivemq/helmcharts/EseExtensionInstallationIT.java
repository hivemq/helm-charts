package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import com.hivemq.openapi.HivemqClusterStatus;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class EseExtensionInstallationIT {
    private static final @NotNull String CHART_NAME = "local-hivemq";
    @Container
    private final @NotNull OperatorHelmChartContainer
            container = new OperatorHelmChartContainer(DockerImageNames.K3s.V1_27,
            "k3s.dockerfile",
            "values/test-values.yaml",
            CHART_NAME)
            .withLocalImages();

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExtensionConfiguration_hivemqRunning() throws Exception {

        final var client = container.getKubernetesClient();

        final URL resource = getClass().getResource("/ese-config.xml");

        final var configMapData = Files.readString(Path.of(Objects.requireNonNull(resource).toURI()));
        final var eseConfigMap = new ConfigMapBuilder().withNewMetadata()
                .withName("ese-extension-config")
                .endMetadata()
                .withData(Map.of("hivemq-enterprise-security-extension.xml", configMapData))
                .build();

        client.configMaps().inNamespace("default").createOrReplace(eseConfigMap);

        container.upgradeLocalChart(CHART_NAME, "/values/ese-extension-values.yaml");

        container.waitForClusterState(HivemqClusterStatus.State.UPDATING);
        container.waitForClusterState(HivemqClusterStatus.State.RUNNING);

        final var deployment = client.apps().deployments().inNamespace("default").withName("local-hivemq").get();
        assertNotNull(deployment);
        final var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertNotNull(container);
        assertEquals("hivemq", container.getName());
        final var foundMount = container.getVolumeMounts().stream().filter(v -> v.getName().contains("ese-extension-config")).findFirst();
        assertTrue(foundMount.isPresent());
        assertEquals("/conf-override/extensions/hivemq-enterprise-security-extension", foundMount.get().getMountPath());
    }
}
