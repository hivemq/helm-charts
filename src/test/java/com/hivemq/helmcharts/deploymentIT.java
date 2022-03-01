package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.HelmK3sContainer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.BindMode.READ_ONLY;

public class deploymentIT {

    private final @NotNull Logger log = LoggerFactory.getLogger(deploymentIT.class);
    public HelmK3sContainer container;

    /*@Test
    public void deployCluster() throws IOException, InterruptedException {
        try (var k3sContainer = new K3sContainer(DockerImageName.parse("kube:latest").asCompatibleSubstituteFor("rancher/k3s"))) {
            k3sContainer.addFileSystemBind(new File(".").getCanonicalPath(), "/test", READ_ONLY);
            k3sContainer.addExposedPort(1883);
            k3sContainer.start();
            var port = k3sContainer.getMappedPort(1883);
            System.out.println(port);
            boolean isCreated = k3sContainer.isCreated();
            assertTrue(isCreated);
            var config = k3sContainer.getKubeConfigYaml();
            var outNodes = k3sContainer.execInContainer("/bin/kubectl", "get", "nodes");
            System.out.println(outNodes.getStdout());
            System.err.println(outNodes.getStderr());
            //helm --kubeconfig /etc/rancher/k3s/k3s.yaml install hivemq .
            var outDeploy = k3sContainer.execInContainer("/bin/helm",
                    "--kubeconfig",
                    "/etc/rancher/k3s/k3s.yaml",
                    "install",
                    "hivemq",
                    "/test/charts/hivemq-operator",
                    "-f",
                    "/test/src/test/resources/testValues.yaml");
            System.out.println(outDeploy.getStdout());
            System.err.println(outDeploy.getStderr());
            assertFalse(outDeploy.getStderr().isEmpty());
            assertTrue(outDeploy.getStdout().contains("STATUS: deployed"));
            //We can not run this until the hivemq pod is ready
            var outExpose = k3sContainer.execInContainer("/bin/kubectl",
                    "expose",
                    "deployment",
                    "hivemq",
                    "--type=LoadBalancer",
                    "--name=hivemq-external");
            System.out.println(outExpose.getStdout());
            System.err.println(outExpose.getStderr());
            assertFalse(outExpose.getStderr().isEmpty());
        }
    }*/
    @Test
    public void testCustomContainer() throws IOException, ApiException, InterruptedException {
        int mqttPort = 1883;
        container = HelmK3sContainer.builder()
                .k3sVersion("v1.21.10-k3s1")
                .tempDir(new File("./src/test/resources"))
                .build();
        final String configYml = prepareContainer(mqttPort);
        deployChart();
        exposeService(configYml,container.getMappedPort(mqttPort));
    }

    private void exposeService(final @NotNull String configYml,int port) throws IOException, ApiException {
        ApiClient client = Config.fromConfig(new StringReader(configYml));
        CoreV1Api api = new CoreV1Api(client);
        var nodes = api.listNode(null,null,null,null,null,null,null,null,null,null);
        System.out.println(nodes.getItems().size());
    }

    public void deployChart() throws IOException, InterruptedException {
        //helm --kubeconfig /etc/rancher/k3s/k3s.yaml install hivemq /test/charts/hivemq-operator -f /test/src/test/resources/testValues.yaml
        var outDeploy = container.execInContainer("/bin/helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                "install",
                "hivemq",
                "/test/charts/hivemq-operator",
                "-f",
                "/test/src/test/resources/testValues.yaml");
        System.out.println(outDeploy.getStdout());
        System.err.println(outDeploy.getStderr());
        assertFalse(outDeploy.getStderr().isEmpty());
        assertTrue(outDeploy.getStdout().contains("STATUS: deployed"));
    }

    private String prepareContainer(int port) throws IOException {
        container.addFileSystemBind(new File(".").getCanonicalPath(), "/test", READ_ONLY);
        container.addExposedPort(port);
        container.start();
        assertTrue(container.isCreated());
        return container.getKubeConfigYaml();
    }

    @AfterEach
    public void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

}
