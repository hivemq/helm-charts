package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.io.File;

public class HelmK3sContainer extends GenericContainer {
    private HelmK3sContainer(final @NotNull String k3sVersion, final @NotNull File tempDir) {
        super(getAdHocImageName(k3sVersion, tempDir));
    }

    public static @NotNull DockerImageName getAdHocImageName(final @NotNull String k3sVersion, final @NotNull File tempDir) {
        final String s = new ImageFromDockerfile().withDockerfile(tempDir.toPath().resolve("Dockerfile"))
                .withBuildArg("K3S_VERSION", k3sVersion)
                .get();
        return DockerImageName.parse(s).asCompatibleSubstituteFor("rancher/k3s");
    }
}
