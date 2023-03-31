package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;

public class DockerImageNames {
    private DockerImageNames() {
    }

    public enum K3s {
        V1_20("v1.20.15-k3s1"),
        V1_21("v1.21.14-k3s1"),
        V1_22("v1.22.17-k3s1"),
        V1_23("v1.23.17-k3s1"),
        V1_24("v1.24.11-k3s1"),
        V1_25("v1.25.8-k3s1"),
        V1_26("v1.26.3-k3s1");
        private final @NotNull String version;

        K3s(final @NotNull String version) {
            this.version = version;
        }

        public @NotNull String getVersion() {
            return version;
        }

        public @NotNull String getImage() {
            return "rancher/k3s:" + version;
        }
    }
}
