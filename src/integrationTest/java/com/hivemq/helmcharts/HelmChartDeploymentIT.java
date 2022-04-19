package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.TestUtils;
import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
@Testcontainers
public class HelmChartDeploymentIT {

    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    @ParameterizedTest
    @ValueSource(strings = {"v1.18.20-k3s1", "v1.19.16-k3s1", "v1.20.15-k3s1", "v1.21.10-k3s1", "v1.22.7-k3s1", "v1.23.4-k3s1"})
    public void withHelmVersionDeployment_mqttMessagePublishedReceived(final @NotNull String version) throws Exception {
        try (var container = OperatorHelmChartContainer.builder()
                .k3sVersion(version)
                .dockerfile(new File("./src/integrationTest/resources/Dockerfile"))
                .helmChartMountPath(new File(".")).containerPath("/test").build()) {

            container.start();

            var valuesPath = new File("/test/src/integrationTest/resources/testValues.yaml");
            var operatorPath = new File("/test/charts/hivemq-operator");


            final var outUpdate = container
                    .execInContainer("/bin/helm", "dependency", "update", operatorPath.getAbsolutePath() + "/");

            assertTrue(outUpdate.getStderr().isEmpty());

            var execDeploy = container
                    .execInContainer("/bin/helm", "--kubeconfig", "/etc/rancher/k3s/k3s.yaml", "install",
                            "hivemq", operatorPath.getAbsolutePath(), "-f", valuesPath.getAbsolutePath());

            if (!execDeploy.getStderr().isEmpty()) {
                // Shows also warnings
                System.err.println(execDeploy.getStderr());
            }

            assertTrue(execDeploy.getStdout().contains("STATUS: deployed"));

            assertTrue(TestUtils.getWaitForClusterToBeReadyLatch(container.getKubeConfigYaml()).await(3, TimeUnit.MINUTES));

            TestUtils.sendTestMessage(container.getMappedPort(1883));

            container.stop();
        }

    }



}
