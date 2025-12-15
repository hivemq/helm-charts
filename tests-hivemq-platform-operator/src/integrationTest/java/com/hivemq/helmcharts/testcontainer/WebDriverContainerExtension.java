package com.hivemq.helmcharts.testcontainer;

import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.selenium.BrowserWebDriverContainer;

import java.util.concurrent.atomic.AtomicReference;

public class WebDriverContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(WebDriverContainerExtension.class);
    private static final @NotNull String STORE_KEY = "webDriverContainer";

    private final @NotNull AtomicReference<BrowserWebDriverContainer> browserWebDriverContainerRef =
            new AtomicReference<>();
    private final @NotNull Network network;

    public WebDriverContainerExtension(final @NotNull Network network) {
        this.network = network;
    }

    @Override
    public void beforeAll(final @NotNull ExtensionContext context) {
        final var store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
        browserWebDriverContainerRef.set((BrowserWebDriverContainer) store.computeIfAbsent(STORE_KEY, key -> {
            LOG.info("Creating shared BrowserWebDriverContainer instance");
            final var container =
                    new BrowserWebDriverContainer(OciImages.getImageName("selenium/standalone-firefox")) //
                            .withNetwork(network) //
                            // needed for Docker on Linux
                            .withExtraHost("host.docker.internal", "host-gateway");
            container.start();
            LOG.info("BrowserWebDriverContainer started successfully");
            return container;
        }));
    }

    @Override
    public void afterAll(final @NotNull ExtensionContext context) {
        browserWebDriverContainerRef.set(null);
    }

    public @NotNull BrowserWebDriverContainer getWebDriverContainer() {
        return browserWebDriverContainerRef.get();
    }
}
