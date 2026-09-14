package com.hivemq.helmcharts.helmimage;

import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Helm test image before it is published, so that a broken image is caught on the pull request rather
 * than after the publishing workflow has run on master.
 */
class HelmImageIT {

    private static final String IMAGE_TAG = System.getProperty("helm.image.tag");
    private static final String EXPECTED_VERSION = System.getProperty("helm.version");

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void helmBinary_reportsThePinnedVersion() {
        final var imageName = OciImages.getImageName("hivemq/helm-test-image:" + IMAGE_TAG);
        try (final var container = new GenericContainer<>(imageName)) {
            // the image has no entry point and no shell, so the binary is invoked directly and the container exits
            container.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("helm"));
            container.withCommand("version", "--short");
            container.withStartupCheckStrategy(new OneShotStartupCheckStrategy());
            container.start();

            assertThat(container.getLogs().trim()).startsWith(EXPECTED_VERSION);
        }
    }
}
