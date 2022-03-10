package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.File;

public class OperatorHelmChartContainer extends K3sContainer {
    public final static int mqttPort = 1883;

    private OperatorHelmChartContainer(final @NotNull String k3sVersion,
                                       final @NotNull File dockerfile,
                                       final @NotNull File helmChartMountPath,
                                       final @NotNull String containerPath) {
        super(getAdHocImageName(k3sVersion, dockerfile));
        final MountableFile helmChartMountable = MountableFile.forHostPath(helmChartMountPath.getPath());
        super.withCopyFileToContainer(helmChartMountable, containerPath);
        super.addExposedPort(mqttPort);
    }

    public static @NotNull DockerImageName getAdHocImageName(final @NotNull String k3sVersion,
                                                             final @NotNull File dockerfile) {
        final String s = new ImageFromDockerfile().withDockerfile(
                        dockerfile.toPath())
                .withBuildArg("K3S_VERSION", k3sVersion)
                .get();
        return DockerImageName.parse(s).asCompatibleSubstituteFor("rancher/k3s");
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public int getMappedPort() {
        return super.getMappedPort(mqttPort);
    }

    public void waitForMqttService() {
        super.withExposedPorts(mqttPort).waitingFor(Wait.forListeningPort());
    }

    public static class Builder {

        private @Nullable File dockerfile;
        private @Nullable File helmChartMountPath;
        private @Nullable String k3sVersion;
        private @Nullable String containerPath;

        public @NotNull Builder dockerfile(final @NotNull File dockerfile) {
            this.dockerfile = dockerfile;
            return this;
        }

        public @NotNull Builder helmChartMountPath(final @NotNull File mountPath) {
            this.helmChartMountPath = mountPath;
            return this;
        }

        public @NotNull Builder k3sVersion(final @NotNull String k3sVersion) {
            this.k3sVersion = k3sVersion;
            return this;
        }

        public @NotNull Builder containerPath(final @NotNull String containerPath) {
            this.containerPath = containerPath;
            return this;
        }

        public @NotNull OperatorHelmChartContainer build() {
            assert dockerfile != null;
            assert k3sVersion != null;
            assert helmChartMountPath != null;
            assert containerPath != null;
            return new OperatorHelmChartContainer(k3sVersion, dockerfile, helmChartMountPath, containerPath);
        }
    }
}
