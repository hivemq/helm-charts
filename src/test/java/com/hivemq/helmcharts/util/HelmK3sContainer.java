package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;

public class HelmK3sContainer extends K3sContainer {
    private HelmK3sContainer(final @NotNull String k3sVersion,
                             final @NotNull File tempDir) {
        super(getAdHocImageName(k3sVersion,
                tempDir));
    }

    public static @NotNull DockerImageName getAdHocImageName(final @NotNull String k3sVersion,
                                                             final @NotNull File tempDir) {
        System.out.println(tempDir.getAbsolutePath());
        final String s = new ImageFromDockerfile().withDockerfile(
                        tempDir.toPath().resolve("Dockerfile"))
                .withBuildArg("K3S_VERSION", k3sVersion)
                .get();
        return DockerImageName.parse(s).asCompatibleSubstituteFor("rancher/k3s");
    }
    public static @NotNull Builder builder() {
        return new Builder();
    }
    public static class Builder {

        private @Nullable File tempDir;
        private @Nullable String k3sVersion;

        public @NotNull Builder tempDir(final @NotNull File tempDir) {
            this.tempDir = tempDir;
            return this;
        }

        public @NotNull Builder k3sVersion(final @NotNull String k3sVersion) {
            this.k3sVersion = k3sVersion;
            return this;
        }

        public @NotNull HelmK3sContainer build() {
            assert tempDir != null;
            assert k3sVersion != null;
            return new HelmK3sContainer(k3sVersion, tempDir);
        }
    }
}
