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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.BindMode.READ_ONLY;

public class deploymentIT {

    private final @NotNull Logger log = LoggerFactory.getLogger(deploymentIT.class);


    @Test
    public void deployCluster() throws IOException, ApiException, InterruptedException {
        var k3sContainer = new K3sContainer(DockerImageName.parse("kube:latest").asCompatibleSubstituteFor("rancher/k3s"));
        k3sContainer.addFileSystemBind(new File(".").getCanonicalPath(), "/test", READ_ONLY);
        k3sContainer.start();
        var config = k3sContainer.getKubeConfigYaml();
        var out = k3sContainer.execInContainer("/bin/kubectl", "get", "nodes");
        System.out.println(out.getStdout());
        System.err.println(out.getStderr());
        k3sContainer.getLogs();
        boolean isCreated = k3sContainer.isCreated();
        assertTrue(isCreated);
        k3sContainer.close();
    }

}
