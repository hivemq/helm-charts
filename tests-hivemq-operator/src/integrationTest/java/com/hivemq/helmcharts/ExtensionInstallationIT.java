package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

@Tag("Extensions")
@Testcontainers
class ExtensionInstallationIT {

    private static final @NotNull String CHART_NAME = "local-hivemq";

    @Container
    private final @NotNull OperatorHelmChartContainer container =
            new OperatorHelmChartContainer(DockerImageNames.K3s.DEFAULT,
                    "values/test-values.yaml",
                    CHART_NAME).withLocalImages();

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void withLogExtensionConfiguration_hivemqRunning() throws Exception {
        final var client = container.getKubernetesClient();
        final var namespace = "default";
        container.upgradeLocalChart(CHART_NAME, "/values/log-extension-values.yaml");
        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Updating");
        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Running");
    }
}
