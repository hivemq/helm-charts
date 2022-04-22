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
    @Container
    private static final @NotNull OperatorHelmChartContainer container = OperatorHelmChartContainer.builder()
            .k3sVersion("v1.20.15-k3s1")
            .dockerfile(new File("./src/integrationTest/resources/Dockerfile"))
            .helmChartMountPath(new File(".")).containerPath("/test").build();

    @Test
    public void withCustomImage_mqttMessagePublishedReceived() throws IOException, InterruptedException {
        System.out.println(Runtime.getRuntime().maxMemory());
        var image=MountableFile.forClasspathResource("hivemq-image.tgz");
        container.addFileSystemBind(image.getFilesystemPath(),"/opt/hivemq-image.tgz", BindMode.READ_ONLY);
        container.start();
        System.out.println(container.getKubeConfigYaml());
        container.copyFileToContainer(MountableFile.forClasspathResource("decompress.sh"),"/opt/");
        var outLoadImage = container.execInContainer("/bin/sh","/opt/decompress.sh");
        assertFalse(outLoadImage.getStdout().isEmpty());
        var valuesPath = new File("/test/src/integrationTest/resources/permissionsDeployment.yaml");
        var operatorPath = new File("/test/charts/hivemq-operator");


        final var outUpdate = container
                .execInContainer("/bin/helm", "dependency", "update", operatorPath.getAbsolutePath() + "/");

        assertTrue(outUpdate.getStderr().isEmpty());

        var execDeploy = container
                .execInContainer("/bin/helm", "--kubeconfig", "/etc/rancher/k3s/k3s.yaml", "install",
                        "hivemq", operatorPath.getAbsolutePath(), "-f", valuesPath.getAbsolutePath());

        if (!execDeploy.getStderr().isEmpty()) {
            // Shows also warnings
            System.err.println(execDeploy.getStderr());
        }

        assertTrue(execDeploy.getStdout().contains("STATUS: deployed"));
        assertTrue(TestUtils.getWaitForClusterToBeReadyLatch(container.getKubeConfigYaml()).await(3, TimeUnit.MINUTES));
        TestUtils.sendTestMessage(container.getMappedPort(1883));
        container.stop();
    }
}
