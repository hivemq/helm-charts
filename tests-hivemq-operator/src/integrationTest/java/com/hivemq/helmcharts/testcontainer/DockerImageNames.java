package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;

public class DockerImageNames {

    private DockerImageNames() {
    }

    public enum K3s {
        // https://hub.docker.com/r/rancher/k3s/tags?page=1&name=v1.31
        V1_20("v1.20.15-k3s1"),
        V1_21("v1.21.14-k3s1"),
        V1_22("v1.22.17-k3s1"),
        V1_23("v1.23.17-k3s1"),
        V1_24("v1.24.17-k3s1"),
        V1_25("v1.25.16-k3s4"),
        V1_26("v1.26.15-k3s1"),
        V1_27("v1.27.16-k3s1"),
        V1_28("v1.28.13-k3s1"),
        V1_29("v1.29.8-k3s1"),
        V1_30("v1.30.4-k3s1"),
        V1_31("v1.31.0-k3s1"),

        MINIMUM(V1_20.version),
        DEFAULT(V1_27.version),
        LATEST(V1_31.version);

        private final @NotNull String version;

        K3s(final @NotNull String version) {
            this.version = version;
        }

        public @NotNull String getVersion() {
            return version;
        }
    }
}
