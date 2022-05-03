package com.hivemq.helmcharts.util;

import com.github.dockerjava.api.DockerClient;
import com.hivemq.crd.hivemq.HiveMQInfo;
import com.hivemq.openapi.HivemqClusterStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;

/**
 * Container that includes the helm binary to be able to install the HiveMQ helm charts
 */
public class OperatorHelmChartContainer extends K3sContainer {
    public final static int mqttPort = 1883;

    public OperatorHelmChartContainer(final @NotNull String k3sVersion,
                                      final @NotNull String dockerfileName,
                                      final @NotNull String customValuesFile) {
        super(getAdHocImageName(k3sVersion, dockerfileName));
        try (K3sContainer k3sContainer = withStartupCheckStrategy(new DeploymentStatusStartupCheckStrategy(this))) {
            assertNotNull(k3sContainer);
            super.addExposedPort(mqttPort);
            super.withFileSystemBind("./build/container/context", "/build", BindMode.READ_ONLY);
            super.withFileSystemBind("./charts/hivemq-operator", "/chart");
            super.withCopyFileToContainer(MountableFile.forClasspathResource(customValuesFile), "/files/values.yml");
        }
    }

    public static @NotNull DockerImageName getAdHocImageName(final @NotNull String k3sVersion,
                                                             final @NotNull String dockerfileName) {
        var dockerfile = new File(MountableFile.forClasspathResource(dockerfileName).getFilesystemPath());

        final String s = new ImageFromDockerfile().withDockerfile(dockerfile.toPath())
                .withBuildArg("K3S_VERSION", k3sVersion)
                .get();

        return DockerImageName.parse(s).asCompatibleSubstituteFor("rancher/k3s");
    }

    private static class DeploymentStatusStartupCheckStrategy extends StartupCheckStrategy {
        private final @NotNull OperatorHelmChartContainer container;

        public DeploymentStatusStartupCheckStrategy(@NotNull OperatorHelmChartContainer container) {
            this.container = container;
            this.withTimeout(Duration.ofSeconds(120));
        }

        @Override
        public @NotNull StartupStatus checkStartupState(@NotNull DockerClient dockerClient, @NotNull String containerId) {
            var s = container.getLogs(STDERR);
            while (!s.matches("(?s).*Node controller sync successful.*")){
                s = container.getLogs(STDERR);
            }
            try {
                // we need this to have the yaml read from the container
                container.containerIsStarted(container.getContainerInfo());
                var yaml = container.getKubeConfigYaml();
                assertNotNull(yaml);
                deployLocalOperator();
                waitForClusterToBeReady(yaml);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            return StartupStatus.SUCCESSFUL;
        }
        public void waitForClusterToBeReady(final @NotNull String kubeConfigYaml) throws InterruptedException {
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

            final var outUpdate = container
                    .execInContainer("/bin/helm", "dependency", "update",  "/chart/");

            assertTrue(outUpdate.getStderr().isEmpty());

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
