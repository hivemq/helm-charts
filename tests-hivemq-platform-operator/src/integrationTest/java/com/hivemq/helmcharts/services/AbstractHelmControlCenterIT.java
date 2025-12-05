package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.selenium.BrowserWebDriverContainer;

import java.nio.file.Path;

@Testcontainers
class AbstractHelmControlCenterIT extends AbstractHelmChartIT {

    static final int CC_SERVICE_PORT_8081 = 8081;
    static final int CC_SERVICE_PORT_8443 = 8443;
    static final int CC_SERVICE_PORT_8444 = 8444;
    static final @NotNull String CC_SERVICE_NAME_8081 = "hivemq-test-hivemq-platform-cc-" + CC_SERVICE_PORT_8081;
    static final @NotNull String CC_SERVICE_NAME_8443 = "hivemq-test-hivemq-platform-cc-" + CC_SERVICE_PORT_8443;
    static final @NotNull String CC_SERVICE_NAME_8444 = "hivemq-test-hivemq-platform-cc-" + CC_SERVICE_PORT_8444;
    static final @NotNull String CC_CUSTOM_SERVICE_NAME = "control-center-service";

    @TempDir
    @NotNull Path tmp;

    @Container
    static final @NotNull BrowserWebDriverContainer WEB_DRIVER_CONTAINER =
            new BrowserWebDriverContainer(OciImages.getImageName("selenium/standalone-firefox")) //
                    .withNetwork(network) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway");
}
