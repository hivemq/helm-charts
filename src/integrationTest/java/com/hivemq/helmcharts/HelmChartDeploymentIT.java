package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import com.hivemq.helmcharts.util.OperatorHelmChartUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
@Testcontainers
public class HelmChartDeploymentIT {

    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    @ParameterizedTest
    @ValueSource(strings = {"v1.18.20-k3s1", "v1.19.16-k3s1", "v1.20.15-k3s1", "v1.21.10-k3s1", "v1.22.7-k3s1", "v1.23.4-k3s1"})
    public void withHelmVersionDeployment_mqttMessagePublishedReceived(final @NotNull String version) throws Exception {
        try (var container = OperatorHelmChartContainer.builder()
                .k3sVersion(version)
                .dockerfile(new File("./src/integrationTest/resources/Dockerfile"))
                .helmChartMountPath(new File("."))
                .containerPath("/test")
                .build()) {

            container.start();

            final var utils = new OperatorHelmChartUtils(container);

            final ExecResult execDeploy = utils.deployChart(
                    new File("/test/src/integrationTest/resources/testValues.yaml"),
                    new File("/test/charts/hivemq-operator"));
            if (!execDeploy.getStderr().isEmpty()) {
                System.err.println(execDeploy.getStderr());
            }
            assertTrue(execDeploy.getStdout().contains("STATUS: deployed"));

            final String podLog = utils.exposeService();
            assertTrue(podLog.contains("Started HiveMQ in"));

            final var receivedMessage = utils.testMqttClient("Sending Message");
            assertNotNull(receivedMessage);
            assertTrue(receivedMessage.getPayload().isPresent());
            assertEquals("Sending Message", StandardCharsets.UTF_8.decode(receivedMessage.getPayload()
                    .get().asReadOnlyBuffer()).toString());

            container.stop();
        }

    }


}
