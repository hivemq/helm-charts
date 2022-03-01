package com.hivemq.helmcharts;

import io.kubernetes.client.openapi.ApiException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.BindMode.READ_ONLY;

public class deploymentIT {

    private final @NotNull Logger log = LoggerFactory.getLogger(deploymentIT.class);

    @Test
    public void deployCluster() throws IOException, ApiException, InterruptedException {
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
    }

}
