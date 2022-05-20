package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@Testcontainers
public class HelmChartDeploymentIT {

    @Container
    private static final @NotNull OperatorHelmChartContainer
            container = new OperatorHelmChartContainer("v1.23.4-k3s1",
            "k3s.dockerfile",
            "values/customTestValues.yaml")
            .withCopyFileToContainer(MountableFile.forClasspathResource("manifest/cluster.yml"),"/files/cluster.yml");


    @Test
    public void withCustomImage_mqttMessagePublishedReceived() {
        System.out.println("is running:" + container.isRunning());

        System.out.println(Runtime.getRuntime().maxMemory());

        container.stop();
    }
}
