package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test capability that allow us to import images properly
 */
@Testcontainers
public class LocalImageDeploymentIT {

    @Container
    private final @NotNull K3sContainer container = new K3sContainer(DockerImageName
            .parse(DockerImageNames.K3s.V1_26.getImage()))
            .withFileSystemBind("./build/containers", "/build", BindMode.READ_ONLY);

    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    @Test
    public void withLocalImages_mqttMessagePublishedReceived() throws Exception {
        var outLoadImage = container.execInContainer("/bin/ctr",
                "images",
                "import",
                "/build/" + "hivemq-k8s-image.tar");

        assertEquals(0, outLoadImage.getExitCode());

        final var outListImages = container.execInContainer("/bin/ctr", "images", "ls");
        assertTrue(outListImages.getStdout().contains("hivemq/hivemq4-test"));
    }
}
