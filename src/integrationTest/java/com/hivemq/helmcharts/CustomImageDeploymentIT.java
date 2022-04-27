package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.TestUtils;
import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.TimeUnit;

import static com.github.javaparser.utils.Utils.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class CustomImageDeploymentIT {

    private static final @NotNull String resourcesPath = "/test";
    private static final @NotNull String customValuesPath = resourcesPath+
            "/src/integrationTest/resources/customImageDeployment.yaml";


    @Container
    private static final @NotNull OperatorHelmChartContainer container = OperatorHelmChartContainer.builder()
            .k3sVersion("v1.20.15-k3s1")
                .dockerfile(new File("./src/integrationTest/resources/context/Dockerfile"))
            .helmChartMountPath(new File(".")).containerPath(customValuesPath).build();


    @Test
    public void withCustomImage_mqttMessagePublishedReceived() throws Exception {
        var customValues = new File(customValuesPath).getAbsolutePath();

        container.start();

        container.copyFileToContainer(MountableFile.forHostPath("./build/container/context/hivemq4-k8s-test.tar"),
                "/opt/hivemq-image.tar");

        var outLoadImage = container.execInContainer("/bin/ctr",
                "images",
                "import",
                "/opt/hivemq-image.tar");

        assertNotNull(outLoadImage.getStdout());

        var deploy = TestUtils.deployLocalOperator(container,resourcesPath,customValues);

        assertTrue(deploy.contains("STATUS: deployed"));
        assertTrue(TestUtils.getWaitForClusterToBeReadyLatch(container.getKubeConfigYaml()).await(3, TimeUnit.MINUTES));

        TestUtils.sendTestMessage(container.getMappedPort(1883));

        container.stop();
    }

}
