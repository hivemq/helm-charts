package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.utility.DockerImageName;

public class DockerImageNames {

    private DockerImageNames() {
    }

    public static final @NotNull DockerImageName K3S_DOCKER_IMAGE = DockerImageName.parse("rancher/k3s")
            .withTag(System.getProperty("k3s.tag", "latest"))
            .asCompatibleSubstituteFor("rancher/k3s");
}
