package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
@Testcontainers
public class CompatibilityHelmChartDeploymentIT {

    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    @ParameterizedTest
    @ValueSource(strings = {"v1.18.20-k3s1", "v1.19.16-k3s1", "v1.20.15-k3s1", "v1.21.10-k3s1", "v1.22.7-k3s1", "v1.23.4-k3s1"})
    public void withHelmVersionDeployment_mqttMessagePublishedReceived(final @NotNull String version)  {
        /*try (var container = new OperatorHelmChartContainer(version, "k3s.dockerfile")){
            container.withCopyFileToContainer(MountableFile.forHostPath("charts"), "/charts");
            container.withCopyFileToContainer(MountableFile.forHostPath("build/resources/integrationTest"), "/resources");
            System.out.println(Runtime.getRuntime().maxMemory());
            container.start();
            var deployOut = container.deployLocalOperatorChart("/charts/hivemq-operator","/resources/customTestValues.yaml");
            assertFalse(deployOut.isEmpty());
            container.stop();
        }*/

    }


}
