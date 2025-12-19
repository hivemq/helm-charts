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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JUnit 5 extension for managing a shared WebDriver container instance across multiple test classes.
 * This extension creates a single Selenium WebDriver container that is reused by all tests, improving
 * test performance and resource utilization.
 *
 * <p>The container is configured with:
 * <ul>
 *   <li>Firefox browser (standalone)</li>
 *   <li>Connection to the provided network</li>
 *   <li>Extra host mapping for Docker on Linux compatibility</li>
 * </ul>
 *
 * <p>By default, the container is managed by the JUnit global store and will not be explicitly stopped
 * by this extension (relies on Testcontainers shutdown hooks). Set {@code stopInstances = true} in the
 * constructor to enable explicit container stopping for debugging purposes.
 */
public class WebDriverContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(WebDriverContainerExtension.class);
    private static final @NotNull String STORE_KEY = "webDriverContainer";

    private final @NotNull AtomicBoolean stopInstances = new AtomicBoolean(false);
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
        if (stopInstances.getAndSet(false)) {
            final var container = browserWebDriverContainerRef.getAndSet(null);
            if (container != null) {
                LOG.info("Stopping shared BrowserWebDriverContainer instance");
                container.stop();
            }
        } else {
            browserWebDriverContainerRef.set(null);
        }
    }

    public @NotNull BrowserWebDriverContainer getWebDriverContainer() {
        return browserWebDriverContainerRef.get();
    }
}
