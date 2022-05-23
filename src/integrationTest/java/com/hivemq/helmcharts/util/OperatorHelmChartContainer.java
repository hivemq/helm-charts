package com.hivemq.helmcharts.util;

import com.github.dockerjava.api.DockerClient;
import com.hivemq.crd.hivemq.HiveMQInfo;
import com.hivemq.openapi.HivemqClusterStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;

/**
 * Container that includes the helm binary to be able to install the HiveMQ helm charts
 */
public class OperatorHelmChartContainer extends K3sContainer {
    public final static int mqttPort = 1883;
    private boolean withCustomImages = false;
    private final @NotNull List<String> imagesNames;

    public OperatorHelmChartContainer(final @NotNull String k3sVersion,
                                      final @NotNull String dockerfileName,
                                      final @NotNull String customValuesFile) {
        super(getAdHocImageName(k3sVersion, dockerfileName));
        //assertNotNull(k3sContainer);
        super.addExposedPort(mqttPort);
        super.withFileSystemBind("./charts/hivemq-operator", "/chart");
        super.withCopyFileToContainer(MountableFile.forClasspathResource(customValuesFile), "/files/values.yml");
        super.withCopyFileToContainer(MountableFile.forClasspathResource("scripts/"), "/scripts");
        super.withStartupCheckStrategy(new DeploymentStatusStartupCheckStrategy(this));
        imagesNames = new ArrayList<>();
    }

    /**
     * Uses custom images instead of docker hub images, additional images can be added
     */
    public @NotNull OperatorHelmChartContainer withCustomImages(@Nullable String...fileNames) {
        withCustomImages = true;
        super.withFileSystemBind("./build/containers", "/build", BindMode.READ_ONLY);
        imagesNames.addAll(Arrays.asList("hivemq-init-dns-image.tar", "hivemq-k8s-image.tar", "hivemq-operator.tar"));
        imagesNames.addAll(Arrays.asList(fileNames));
        return this;
    }

    protected @NotNull List<String> getImagesNames() {
        return imagesNames;
    }

    public static @NotNull DockerImageName getAdHocImageName(final @NotNull String k3sVersion,
                                                             final @NotNull String dockerfileName) {
        var dockerfile = new File(MountableFile.forClasspathResource(dockerfileName).getFilesystemPath());

        final String s = new ImageFromDockerfile().withDockerfile(dockerfile.toPath())
                .withBuildArg("K3S_VERSION", k3sVersion)
                .get();

        return DockerImageName.parse(s).asCompatibleSubstituteFor("rancher/k3s");
    }

    @Override
    public @NotNull OperatorHelmChartContainer withCopyFileToContainer(@NotNull MountableFile mountableFile, @NotNull String containerPath) {
        super.withCopyFileToContainer(mountableFile, containerPath);
        return this;
    }

    private static class DeploymentStatusStartupCheckStrategy extends StartupCheckStrategy {
        private final @NotNull OperatorHelmChartContainer container;

        public DeploymentStatusStartupCheckStrategy(@NotNull OperatorHelmChartContainer container) {
            this.container = container;
            this.withTimeout(Duration.ofSeconds(240));
        }

        @Override
        public @NotNull StartupStatus checkStartupState(@NotNull DockerClient dockerClient, @NotNull String containerId) {
            var s = container.getLogs(STDERR);
            while (!s.matches("(?s).*Node controller sync successful.*")) {
                s = container.getLogs(STDERR);
            }
            try {
                // we need this to have the yaml read from the container
                container.containerIsStarted(container.getContainerInfo());
                var yaml = container.getKubeConfigYaml();
                assertNotNull(yaml);
                if (container.withCustomImages) {
                    loadImages();
                }
                deployLocalOperator();
                waitForClusterToBeReady(yaml);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return StartupStatus.SUCCESSFUL;
        }

        public void loadImages() {
            container.getImagesNames().forEach(a -> {
                try {
                    var outLoadImage = container.execInContainer("/bin/ctr",
                            "images",
                            "import",
                            "/build/" + a);
                    assertFalse(outLoadImage.getStdout().isEmpty());
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

        }

        private void waitForClusterToBeReady(final @NotNull String kubeConfigYaml) throws InterruptedException {
            final CountDownLatch closeLatch = new CountDownLatch(1);
            Config config = Config.fromKubeconfig(kubeConfigYaml);
            DefaultKubernetesClient client = new DefaultKubernetesClient(config);
            client.customResources(HiveMQInfo.class).watch(new Watcher<>() {
                @Override
                public void eventReceived(@NotNull Action action, @NotNull HiveMQInfo resource) {

                    if (resource.getStatus() != null
                            && resource.getStatus().getState() != null
                            && resource.getStatus().getState() == HivemqClusterStatus.State.RUNNING) {
                        closeLatch.countDown();
                    }
                }

                @Override
                public void onClose(@NotNull WatcherException cause) {
                    System.out.println("onClose");
                }
            });
            closeLatch.await();
        }

        private void deployLocalOperator() throws IOException, InterruptedException {
            //helm dependency update /chart
            final var outUpdate = container
                    .execInContainer("/bin/helm", "dependency", "update", "/chart/");

            assertTrue(outUpdate.getStderr().isEmpty());
            // helm --kubeconfig /etc/rancher/k3s/k3s.yaml install hivemq /chart -f /files/values.yml
            var execDeploy = container
                    .execInContainer("/bin/helm", "--kubeconfig", "/etc/rancher/k3s/k3s.yaml", "install",
                            "hivemq", "/chart", "-f", "/files/values.yml");

            if (!execDeploy.getStderr().isEmpty()) {
                // Shows also warnings
                System.err.println(execDeploy.getStderr());
            }
            assertFalse(execDeploy.getStdout().isEmpty());
        }
    }


}
