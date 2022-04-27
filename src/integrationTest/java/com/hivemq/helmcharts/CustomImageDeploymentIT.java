package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class CustomImageDeploymentIT {

    private static final @NotNull String resourcesPath = "/test";

    @Container
    private static final @NotNull OperatorHelmChartContainer container = OperatorHelmChartContainer.builder()
            .k3sVersion("v1.20.15-k3s1")
            .dockerfile("k3s.dockerfile")
            .helmChartMountPath(new File(".")).containerPath(resourcesPath).build();


    @Test
    public void withCustomImage_mqttMessagePublishedReceived() throws Exception {

        container.start();

        var containerPath = "/build/container/context/hivemq4-k8s-test.tar";

        assertTrue(new File("."+containerPath).exists());

        var outLoadImage = container.execInContainer("/bin/ctr",
                "images",
                "import",
                resourcesPath+containerPath);

        assertFalse(outLoadImage.getStdout().isEmpty());

        var outListImages = container.execInContainer("/bin/ctr", "images", "ls");
        assertTrue(outListImages.getStdout().contains("hivemq4-k8s-test"));
        container.stop();
    }

}
