package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import com.hivemq.helmcharts.util.OperatorHelmChartUtils;
import io.kubernetes.client.openapi.ApiException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Container;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that the chart is deployed successfully on specific kubernetes cluster versions
 */
public class helmChartDeploymentIT {

    public @Nullable OperatorHelmChartContainer container;

    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    @ParameterizedTest
    @ValueSource(strings = {"v1.18.20-k3s1", "v1.19.16-k3s1", "v1.20.15-k3s1", "v1.21.10-k3s1", "v1.22.7-k3s1", "v1.23.4-k3s1"})
    public void withHelmVersionDeployment_mqttMessagePublishedReceived(final @NotNull String version) throws IOException, InterruptedException, ApiException {
        container = OperatorHelmChartContainer.builder()
                .k3sVersion(version)
                .dockerfile(new File("./src/integrationTest/resources/Dockerfile"))
                .mountPath(new File("."))
                .containerPath("/test")
                .build();

        container.start();
        var utils = new OperatorHelmChartUtils(container);

        Container.ExecResult execDeploy = utils.deployChart(
                new File("/test/src/integrationTest/resources/testValues.yaml"),
                new File("/test/charts/hivemq-operator"));
        if (!execDeploy.getStderr().isEmpty()) {
            System.err.println(execDeploy.getStderr());
        }
        assertTrue(execDeploy.getStdout().contains("STATUS: deployed"));

        String podLog = utils.exposeService();
        assertTrue(podLog.contains("Started HiveMQ in"));

        var receivedMessage = utils.testMqttClient("Sending Message");
        assertNotNull(receivedMessage);
        assertTrue(receivedMessage.getPayload().isPresent());
        assertEquals("Sending Message", StandardCharsets.UTF_8.decode(receivedMessage.getPayload()
                .get().asReadOnlyBuffer()).toString());
    }

    @AfterEach
    public void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

}
