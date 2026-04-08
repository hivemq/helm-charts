package com.hivemq.helmcharts.services;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.testcontainer.WebDriverContainerExtension;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.selenium.BrowserWebDriverContainer;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("NotNullFieldNotInitialized")
class AbstractHelmControlCenterIT extends AbstractHelmChartIT {

    @RegisterExtension
    private static final @NotNull WebDriverContainerExtension WEB_DRIVER_CONTAINER_EXTENSION =
            new WebDriverContainerExtension(network);

    static final int CC_SERVICE_PORT_8081 = 8081;
    static final int CC_SERVICE_PORT_8443 = 8443;
    static final int CC_SERVICE_PORT_8444 = 8444;
    static final @NotNull String CC_CUSTOM_SERVICE_NAME = "control-center-service";

    final @NotNull String ccServiceName8081 = "hivemq-%s-cc-%s".formatted(platformReleaseName, CC_SERVICE_PORT_8081);
    final @NotNull String ccServiceName8443 = "hivemq-%s-cc-%s".formatted(platformReleaseName, CC_SERVICE_PORT_8443);
    final @NotNull String ccServiceName8444 = "hivemq-%s-cc-%s".formatted(platformReleaseName, CC_SERVICE_PORT_8444);

    @TempDir
    @NotNull Path tmp;

    static @NotNull BrowserWebDriverContainer webDriverContainer;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void setupWebDriverContainer() {
        webDriverContainer = WEB_DRIVER_CONTAINER_EXTENSION.getWebDriverContainer();
    }
}
