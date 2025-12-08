package com.hivemq.helmcharts.testcontainer;

import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;

public class OciUtil {

    private OciUtil() {
    }

    public static @NotNull String resolveLocalImage(final @NotNull String imageName) {
        final var ociImage = OciImages.getImageName(imageName);
        return "host.docker.internal/%s:%s".formatted(ociImage.getRepository(), ociImage.getVersionPart());
    }
}
