package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.TestUtils;
import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
@Testcontainers
public class HelmChartDeploymentIT {

    private static final @NotNull String resourcesPath = "/test";
    private static final @NotNull String customValuesPath = resourcesPath+
            "/src/integrationTest/resources/testValues.yaml";


    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    @ParameterizedTest
    @ValueSource(strings = {"v1.18.20-k3s1", "v1.19.16-k3s1", "v1.20.15-k3s1", "v1.21.10-k3s1", "v1.22.7-k3s1", "v1.23.4-k3s1"})
    public void withHelmVersionDeployment_mqttMessagePublishedReceived(final @NotNull String version) throws Exception {
        var customValues = new File(customValuesPath).getAbsolutePath();
        try (var container = OperatorHelmChartContainer.builder()
                .k3sVersion(version)
                .withTmpFs(true)
                .dockerfile("k3s.dockerfile")
                .helmChartMountPath(new File(".")).containerPath(resourcesPath).build()) {

            container.start();

            var deploy = TestUtils.deployLocalOperator(container,resourcesPath,customValues);

            assertTrue(deploy.contains("STATUS: deployed"));

            assertTrue(TestUtils.getWaitForClusterToBeReadyLatch(container.getKubeConfigYaml()).await(3, TimeUnit.MINUTES));

            TestUtils.sendTestMessage(container.getMappedPort(1883));

            container.stop();
        }

    }



}
