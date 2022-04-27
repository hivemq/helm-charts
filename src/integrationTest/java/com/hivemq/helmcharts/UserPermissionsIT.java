package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.OperatorHelmChartContainer;
import com.hivemq.helmcharts.util.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.github.javaparser.utils.Utils.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class UserPermissionsIT {

    private static final @NotNull String resourcesPath = "/test";
    private static final @NotNull String customValuesPath = resourcesPath+"/src/integrationTest/resources/permissionsDeployment.yaml";

    @Container
    private static final @NotNull OperatorHelmChartContainer container = OperatorHelmChartContainer.builder()
            .k3sVersion("v1.20.15-k3s1")
            .dockerfile(new File("./src/integrationTest/resources/Dockerfile"))
            .helmChartMountPath(new File(".")).containerPath(resourcesPath).build();

    @Test
    public void withCustomImage_mqttMessagePublishedReceived() throws Exception {
        // We need at least 6GB of RAM for this test
        System.out.println(Runtime.getRuntime().maxMemory());

        var customValues = new File(customValuesPath).getAbsolutePath();

        var image=MountableFile.forClasspathResource("hivemq-image.tgz");
        container.addFileSystemBind(image.getFilesystemPath(),"/opt/hivemq-image.tgz", BindMode.READ_ONLY);

        container.start();

        container.copyFileToContainer(MountableFile.forClasspathResource("decompress.sh"),"/opt/");

        var outLoadImage = container.execInContainer("/bin/sh","/opt/decompress.sh");
        assertFalse(outLoadImage.getStdout().isEmpty());

        var deploy = TestUtils.deployLocalOperator(container,resourcesPath,customValues);

        assertTrue(deploy.contains("STATUS: deployed"));

        assertTrue(TestUtils.getWaitForClusterToBeReadyLatch(container.getKubeConfigYaml()).await(3, TimeUnit.MINUTES));

        TestUtils.sendTestMessage(container.getMappedPort(1883));
        container.stop();
    }
}
