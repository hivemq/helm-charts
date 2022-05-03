package com.hivemq.helmcharts;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class CustomImageDeploymentIT {

    @Container
    private static final @NotNull K3sContainer container = new K3sContainer(DockerImageName
            .parse("rancher/k3s:v1.21.3-k3s1"))
            .withFileSystemBind("./build/container/context", "/build", BindMode.READ_ONLY)
            .withFileSystemBind("./charts/hivemq-operator", "/chart");

    @Test
    public void withCustomImage_mqttMessagePublishedReceived() throws Exception {

        System.out.println(Runtime.getRuntime().maxMemory());

        var containerName = "hivemq4-k8s-test";

        var outLoadImage = container.execInContainer("/bin/ctr",
                "images",
                "import",
                "/build/" + containerName + ".tar");

        assertFalse(outLoadImage.getStdout().isEmpty());

        var outListImages = container.execInContainer("/bin/ctr", "images", "ls");
        assertTrue(outListImages.getStdout().contains(containerName));
    }

}
