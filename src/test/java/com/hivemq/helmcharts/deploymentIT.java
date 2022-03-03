package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.exceptions.ConnectionClosedException;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5DisconnectException;
import com.hivemq.helmcharts.util.HelmK3sContainer;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testng.TestException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.BindMode.READ_WRITE;

public class deploymentIT {

    private final @NotNull
    Logger log = LoggerFactory.getLogger(deploymentIT.class);
    public @Nullable
    HelmK3sContainer container;
    private final int mqttPort = 1883;

    @ParameterizedTest
    @ValueSource(strings = {"v1.18.20-k3s1", "v1.19.16-k3s1", "v1.20.15-k3s1", "v1.21.10-k3s1", "v1.22.7-k3s1", "v1.23.4-k3s1"})
    public void withHelmVersionDeployment_mqttMessagePublishedReceived(final @NotNull String version) throws Exception {
        container = HelmK3sContainer.builder()
                .k3sVersion(version)
                .tempDir(new File("./src/test/resources"))
                .build();
        container.addFileSystemBind(new File(".").getCanonicalPath(), "/test", READ_WRITE);
        container.addExposedPort(mqttPort);
        container.start();
        deployChart();
        exposeService();
        testMqttClient();
    }

    private void testMqttClient() throws InterruptedException {
        int retries = 10;
        assert container != null;
        int mappedPort = container.getMappedPort(mqttPort);
        container.withExposedPorts(mqttPort).waitingFor(Wait.forListeningPort());
        System.out.println("Port is ready");
        Mqtt5BlockingClient client = getMqtt5BlockingClient(mappedPort, retries);
        var publishes = client.publishes(MqttGlobalPublishFilter.ALL);
        client.subscribeWith().topicFilter("test").send();
        client.publishWith()
                .topic("test")
                .payload("Test Message".getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send();
        var receivedMessage = publishes.receive();
        assertNotNull(receivedMessage);
        assertTrue(publishes.receive(200, TimeUnit.MILLISECONDS).isEmpty());
    }

    @NotNull
    private Mqtt5BlockingClient getMqtt5BlockingClient(final int mappedPort, int retries) throws RuntimeException {
        Mqtt5BlockingClient client = Mqtt5Client.builder().
                serverPort(mappedPort).buildBlocking();
        try {
            client.connect();
            assertEquals(client.getState(), MqttClientState.CONNECTED);
        } catch (Mqtt5DisconnectException | ConnectionClosedException e) {
            System.err.println("Can not connect to server:" + e.getClass().getName()
                    + " with message: " + e.getMessage()
                    + " retries:"+retries);
            if (retries > 0) {
                return getMqtt5BlockingClient(mappedPort, --retries);
            } else {
                System.err.println("Retries exceeded");
                assert false;
            }
        }
        return client;
    }

    private void exposeService() throws IOException, ApiException, InterruptedException {
        assert container != null;
        String kubeConfigYaml = container.getKubeConfigYaml();
        var client = Config.fromConfig(new StringReader(kubeConfigYaml));
        var api = new CoreV1Api(client);
        var nodes = api.listNode(null, null, null, null, null, null, null, null, null, null);
        assertTrue(nodes.getItems().size() > 0);
        V1Pod foundPods = waitForHiveMQCluster(api);
        assertNotNull(foundPods);
        //When the pod is running the cluster needs time to be ready
        var podLog = new PodLogs(client);
        try (var is = podLog.streamNamespacedPodLog(foundPods)) {
            var scanner = new Scanner(is).useDelimiter("\n");
            while (scanner.hasNext()) {
                var s = scanner.next();
                if (s.contains("Started HiveMQ in")) {
                    System.out.println("Broker Ready");
                    return;
                }
            }
        }
        //ByteStreams.copy(is,System.out);
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
                    assert container != null;
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
                if (defaultPods.get(0).getStatus().getPhase().equalsIgnoreCase("running")) {
                    //We wait for the pod to be on the running phase, hivemq cluster is not ready yet in either case
                    return defaultPods.get(0);
                }
            }
            retries--;
            Thread.sleep(6000);
        }
        throw new TestException("HiveMQ pod not found");
    }

    public void deployChart() throws IOException, InterruptedException {
        //helm --kubeconfig /etc/rancher/k3s/k3s.yaml install hivemq /test/charts/hivemq-operator -f /test/src/test/resources/testValues.yaml
        assert container != null;
        var outUpdate = container.execInContainer("/bin/helm",
                "dependency",
                "update",
                "/test/charts/hivemq-operator/");
        assertTrue(outUpdate.getStderr().isEmpty());
        var outDeploy = container.execInContainer("/bin/helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                "install",
                "hivemq",
                "/test/charts/hivemq-operator",
                "-f",
                "/test/src/test/resources/testValues.yaml");
        System.out.println(outDeploy.getStdout());
        if (!outDeploy.getStderr().isEmpty()) {
            log.warn(outDeploy.getStderr());
        }
        assertTrue(outDeploy.getStdout().contains("STATUS: deployed"));
    }

    @AfterEach
    public void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

}
