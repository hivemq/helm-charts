package com.hivemq.helmcharts;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.helmcharts.util.HelmK3sContainer;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.com.google.common.io.ByteStreams;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.BindMode.READ_WRITE;

public class deploymentIT {

    private final @NotNull
    Logger log = LoggerFactory.getLogger(deploymentIT.class);
    public @Nullable
    HelmK3sContainer container;

    @Test
    public void testCustomContainer() throws IOException, InterruptedException, ApiException {

        container = HelmK3sContainer.builder()
                .k3sVersion("v1.21.10-k3s1")
                .tempDir(new File("./src/test/resources"))
                .build();
        container.addFileSystemBind(new File(".").getCanonicalPath(), "/test", READ_WRITE);

        int mqttPort = 1883;
        container.addExposedPort(mqttPort);
        container.start();
        deployChart();
        exposeService();
        int mappedPort = container.getMappedPort(mqttPort);
        testMqttClient(mappedPort);
    }

    private void testMqttClient(int mappedPort) throws InterruptedException {
        var client = Mqtt5Client.builder().
                serverPort(mappedPort).buildBlocking();
        client.connect();
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
        //var podLog = new PodLogs();
        //var is =podLog.streamNamespacedPodLog(foundPods);
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
        return null;
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
        log.warn(outDeploy.getStderr());
        assertTrue(outDeploy.getStdout().contains("STATUS: deployed"));
    }

    @AfterEach
    public void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

}
