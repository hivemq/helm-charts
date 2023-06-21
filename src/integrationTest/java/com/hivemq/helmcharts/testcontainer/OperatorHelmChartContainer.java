package com.hivemq.helmcharts.testcontainer;

import com.github.dockerjava.api.DockerClient;
import com.hivemq.crd.hivemq.HiveMQInfo;
import com.hivemq.openapi.HivemqClusterStatus;
import io.fabric8.kubernetes.api.model.Pod;
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

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.K3s.V1_24;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;

/**
 * Container that includes the helm binary to be able to install the HiveMQ helm charts
 */
public class OperatorHelmChartContainer extends K3sContainer {
    public final static int MQTT_PORT = 1883;
    private final @NotNull List<String> imagesNames;
    private boolean withCustomImages = false;

    public OperatorHelmChartContainer(final @NotNull DockerImageNames.K3s k3s,
                                      final @NotNull String dockerfileName,
                                      final @NotNull String customValuesFile) {
        super(getAdHocImageName(k3s, dockerfileName));
        super.addExposedPort(MQTT_PORT);
        super.withCopyFileToContainer(MountableFile.forHostPath("./charts/hivemq-operator"), "/chart");
        super.withCopyFileToContainer(MountableFile.forClasspathResource(customValuesFile), "/files/values.yml");
        super.withStartupCheckStrategy(new DeploymentStatusStartupCheckStrategy(this));
        if (k3s.ordinal() > V1_24.ordinal()) {
            super.withCommand("server", "--disable=traefik", "--tls-san=" + getHost());
        }
        imagesNames = new ArrayList<>();
    }

    private static @NotNull DockerImageName getAdHocImageName(final @NotNull DockerImageNames.K3s k3s,
                                                              final @NotNull String dockerfileName) {
        final var dockerfile = new File(MountableFile.forClasspathResource(dockerfileName).getFilesystemPath());

        final var s = new ImageFromDockerfile().withDockerfile(dockerfile.toPath())
                .withBuildArg("K3S_VERSION", k3s.getVersion())
                .get();

        return DockerImageName.parse(s).asCompatibleSubstituteFor("rancher/k3s");
    }

    /**
     * Uses custom images instead of docker hub images, additional images can be appended
     */
    public @NotNull OperatorHelmChartContainer withLocalImages(final @Nullable String... fileNames) {
        withCustomImages = true;
        imagesNames.addAll(Arrays.asList("hivemq-init-dns-image.tar", "hivemq-k8s-image.tar", "hivemq-operator.tar"));
        imagesNames.addAll(Arrays.asList(fileNames));
        super.withFileSystemBind("./build/containers", "/build", BindMode.READ_ONLY);
        return this;
    }

    protected @NotNull List<String> getImagesNames() {
        return imagesNames;
    }

    private static class DeploymentStatusStartupCheckStrategy extends StartupCheckStrategy {
        private final @NotNull OperatorHelmChartContainer container;

        public DeploymentStatusStartupCheckStrategy(@NotNull final OperatorHelmChartContainer container) {
            this.container = container;
            this.withTimeout(Duration.ofSeconds(240));
        }

        @Override
        public @NotNull StartupStatus checkStartupState(final @NotNull DockerClient dockerClient, final @NotNull String containerId) {
            try {
                await().until(() -> container.getLogs(STDERR).matches("(?s).*Node controller sync successful.*"));
                // we need this to have the yaml read from the container
                container.containerIsStarted(container.getContainerInfo());
                final var yaml = container.getKubeConfigYaml();
                assertNotNull(yaml);
                if (container.withCustomImages) {
                    loadImages();
                }

                final var config = Config.fromKubeconfig(yaml);
                try (final DefaultKubernetesClient client = new DefaultKubernetesClient(config)) {

                    deployLocalOperator();
                    waitForClusterToBeReady(client);
                    // get the HiveMQ container logs inside the pod
                    final Pod pod = client.pods().inAnyNamespace().withLabel("app", "hivemq").list().getItems().get(0);
                    final var containerResource = client.pods().inNamespace("default").withName(pod.getMetadata().getName()).inContainer("hivemq");
                    assertFalse(containerResource.getLog().contains("Could not read the configuration file /opt/hivemq/conf/config.xml. Using default config"),
                            "When using the default config a cluster could not be created");
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            return StartupStatus.SUCCESSFUL;
        }

        public void loadImages() {
            container.getImagesNames().forEach(a -> {
                try {
                    final var outLoadImage = container.execInContainer("/bin/ctr",
                            "images",
                            "import",
                            "/build/" + a);
                    assertFalse(outLoadImage.getStdout().isEmpty());
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });

        }

        @SuppressWarnings("resource")
        private void waitForClusterToBeReady(final DefaultKubernetesClient client) throws InterruptedException {
            final var closeLatch = new CountDownLatch(1);
            client.customResources(HiveMQInfo.class).watch(new Watcher<>() {
                @Override
                public void eventReceived(final @NotNull Action action,
                                          final @NotNull HiveMQInfo resource) {

                    if (resource.getStatus() != null
                            && resource.getStatus().getState() != null
                            && resource.getStatus().getState() == HivemqClusterStatus.State.RUNNING) {
                        closeLatch.countDown();
                    }
                }

                @Override
                public void onClose(final @NotNull WatcherException cause) {
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
            final var execDeploy = container
                    .execInContainer("/bin/helm", "--kubeconfig", "/etc/rancher/k3s/k3s.yaml", "install",
                            "hivemq", "/chart", "-f", "/files/values.yml");

            if (!execDeploy.getStderr().isEmpty()) {
                // Shows also warnings
                System.err.println(execDeploy.getStderr());
            }
            assertFalse(execDeploy.getStdout().isEmpty(), execDeploy.getStderr());
        }
    }


}
