package com.hivemq.helmcharts.util;

import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.containers.Container.ExecResult;
import org.testng.TestException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class OperatorHelmChartUtils {

    private final @NotNull OperatorHelmChartContainer container;

    public OperatorHelmChartUtils(@NotNull OperatorHelmChartContainer container) {
        this.container = container;
    }

    public @NotNull Mqtt5Publish testMqttClient(String message) throws InterruptedException {
        container.waitForMqttService();
        System.out.println("Port is ready");
        Mqtt5BlockingClient client = getMqtt5BlockingClient(container.getMappedPort());
        var publishes = client.publishes(MqttGlobalPublishFilter.ALL);
        client.subscribeWith().topicFilter("test").send();
        client.publishWith()
                .topic("test")
                .payload(message.getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send();
        Mqtt5Publish receivedMessage = publishes.receive();
        assertTrue(publishes.receive(200, TimeUnit.MILLISECONDS).isEmpty());
        return receivedMessage;

    }

    @NotNull
    private Mqtt5BlockingClient getMqtt5BlockingClient(final int mappedPort) throws RuntimeException {
        Mqtt5BlockingClient client = Mqtt5Client.builder()
                .automaticReconnectWithDefaultConfig()
                .serverPort(mappedPort)
                .buildBlocking();
        client.connect();
        assertEquals(client.getState(), MqttClientState.CONNECTED);
        System.out.println("Mqtt Client Connected");
        return client;
    }

    public @NotNull ExecResult deployChart(File valuesPath,File operatorPath) throws IOException, InterruptedException {
        //helm --kubeconfig /etc/rancher/k3s/k3s.yaml install hivemq /test/charts/hivemq-operator -f /test/src/test/resources/testValues.yaml
        var outUpdate = container.execInContainer("/bin/helm",
                "dependency",
                "update",
                operatorPath.getAbsolutePath()+"/");
        assertTrue(outUpdate.getStderr().isEmpty());
        return container.execInContainer("/bin/helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                "install",
                "hivemq",
                operatorPath.getAbsolutePath(),
                "-f",
                valuesPath.getAbsolutePath());
    }

    public @NotNull String exposeService() throws IOException, ApiException, InterruptedException {
        String kubeConfigYaml = container.getKubeConfigYaml();
        var client = Config.fromConfig(new StringReader(kubeConfigYaml));
        var api = new CoreV1Api(client);
        var nodes = api.listNode(null, null, null, null, null, null, null, null, null, null);
        assertTrue(nodes.getItems().size() > 0);
        V1Pod foundPods = waitForHiveMQCluster(api);
        assertNotNull(foundPods);
        //When the pod is running the cluster needs time to be ready
        var podLog = new PodLogs(client);
        StringBuilder stringBuilder = new StringBuilder();
        try (var is = podLog.streamNamespacedPodLog(foundPods)) {
            var scanner = new Scanner(is).useDelimiter("\n");
            while (scanner.hasNext()) {
                var s = scanner.next();
                stringBuilder.append(s);
                stringBuilder.append("\n");
                if (s.contains("Started HiveMQ in")) {
                    System.out.println("Broker Ready");
                    break;
                }
            }
        }
        //ByteStreams.copy(is,System.out);
        return stringBuilder.toString();
    }

    private @Nullable V1Pod waitForHiveMQCluster(@NotNull CoreV1Api api) throws ApiException, InterruptedException, IOException {
        int retries = 20;
        boolean serviceReady = false;
        while (retries >= 0) {
            List<V1Pod> defaultPods = api.listNamespacedPod("default",
                    null, null, null, null,
                    "app=hivemq", null, null, null,
                    null, null).getItems();

            if (defaultPods.size() == 1) {
                // As soon as the pod is created we can expose the service
                if (!serviceReady) {
                    var outExpose = container.execInContainer("/bin/kubectl",
                            "expose",
                            "deployment",
                            "hivemq",
                            "--type=LoadBalancer",
                            "--name=hivemq-external");
                    System.out.println(outExpose.getStdout());
                    assertTrue(outExpose.getStderr().isEmpty());
                    serviceReady = true;
                }
                if (Objects.requireNonNull(Objects.requireNonNull(defaultPods.get(0).getStatus()).getPhase()).equalsIgnoreCase("running")) {
                    //We wait for the pod to be on the running phase, hivemq cluster is not ready yet in either case
                    return defaultPods.get(0);
                }
            }
            retries--;
            Thread.sleep(6000);
        }
        throw new TestException("HiveMQ pod not found");
    }
}
