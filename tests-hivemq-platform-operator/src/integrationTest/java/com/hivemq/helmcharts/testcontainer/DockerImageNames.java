package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.utility.DockerImageName;

public class DockerImageNames {

    // used for local testing
    @SuppressWarnings("unused")
    public static final @NotNull DockerImageName TEST_HIVEMQ_DOCKER_IMAGE = DockerImageName.parse("hivemq/hivemq4-test")
            .withTag("snapshot")
            .asCompatibleSubstituteFor("hivemq/hivemq4");

    public static final @NotNull DockerImageName HIVEMQ_DOCKER_IMAGE = DockerImageName.parse("hivemq/hivemq4")
            .withTag(System.getProperty("hivemq.tag", "latest"))
            .asCompatibleSubstituteFor("hivemq/hivemq4");

    public static final @NotNull DockerImageName K3S_DOCKER_IMAGE = DockerImageName.parse("rancher/k3s")
            .withTag(System.getProperty("k3s.tag", "latest"))
            .asCompatibleSubstituteFor("rancher/k3s");

    public static final @NotNull DockerImageName NGINX_DOCKER_IMAGE = DockerImageName.parse("nginx")
            .withTag(System.getProperty("nginx.tag", "latest"))
            .asCompatibleSubstituteFor("nginx");

    public static final @NotNull DockerImageName SELENIUM_DOCKER_IMAGE =
            DockerImageName.parse("selenium/standalone-chrome")
                    .withTag(System.getProperty("selenium.tag", "latest"))
                    .asCompatibleSubstituteFor("selenium/standalone-chrome");

    private DockerImageNames() {
    }
}
