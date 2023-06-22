package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import com.hivemq.openapi.HivemqClusterStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

@Testcontainers
public class ExtensionInstallationIT {
    private static final @NotNull String CHART_NAME = "local-hivemq";
    @Container
    private final @NotNull OperatorHelmChartContainer
            container = new OperatorHelmChartContainer(DockerImageNames.K3s.V1_27,
            "k3s.dockerfile",
            "values/test-values.yaml",
            CHART_NAME)
            .withLocalImages();

    @Test
    @Timeout(value = 5,unit = TimeUnit.MINUTES)
    void withExtensionConfiguration_hivemqRunning() throws Exception {
        container.upgradeLocalChart(CHART_NAME, "/values/extension-values.yaml");
        container.waitForClusterState(HivemqClusterStatus.State.UPDATING);
        container.waitForClusterState(HivemqClusterStatus.State.RUNNING);
    }
}
