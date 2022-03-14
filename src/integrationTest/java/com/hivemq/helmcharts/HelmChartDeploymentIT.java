package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.crd.hivemq.HiveMQInfo;
import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import com.hivemq.openapi.HivemqClusterStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
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

            waitForClusterToBeReady(container);

            Mqtt5BlockingClient client = Mqtt5Client.builder()
                    .automaticReconnectWithDefaultConfig()
                    .serverPort(container.getMappedPort(1883))
                    .buildBlocking();
            client.connect();

            var publishes = client.publishes(MqttGlobalPublishFilter.ALL);
            client.subscribeWith().topicFilter("test").send();
            client.publishWith()
                    .topic("test")
                    .payload("Sending Message".getBytes(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE).send();
            Mqtt5Publish receivedMessage = publishes.receive();
            assertTrue(receivedMessage.getPayload().isPresent());
            assertEquals("Sending Message", StandardCharsets.UTF_8.decode(receivedMessage
                    .getPayload().get().asReadOnlyBuffer()).toString());

            container.stop();
        }

    }

    private void waitForClusterToBeReady(final @NotNull OperatorHelmChartContainer container) {
        String kubeConfigYaml = container.getKubeConfigYaml();
        Config config = Config.fromKubeconfig(kubeConfigYaml);
        DefaultKubernetesClient client = new DefaultKubernetesClient(config);
        var completableFutureResource = new CompletableFuture<Void>();
        client.customResources(HiveMQInfo.class).watch(new Watcher<>() {
            @Override
            public void eventReceived(@NotNull Action action, @NotNull HiveMQInfo resource) {
                if (resource.getStatus() != null
                        && resource.getStatus().getState() != null
                        && resource.getStatus().getState() == HivemqClusterStatus.State.RUNNING) {
                    completableFutureResource.complete(null);
                }
            }

            @Override
            public void onClose(@NotNull WatcherException cause) {
                System.out.println("onClose");
            }
        });
        await().atMost(2, TimeUnit.MINUTES).until(completableFutureResource::isDone);
    }
}
