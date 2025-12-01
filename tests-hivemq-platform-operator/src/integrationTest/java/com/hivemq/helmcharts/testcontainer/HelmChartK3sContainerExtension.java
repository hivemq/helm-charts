package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class HelmChartK3sContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmChartK3sContainerExtension.class);

    private final @NotNull AtomicBoolean stopInstances = new AtomicBoolean(false);
    private final @NotNull AtomicReference<HelmChartK3sContainer> helmChartK3sContainerRef = new AtomicReference<>();
    private final @NotNull AtomicReference<Network> networkRef = new AtomicReference<>();

    private final boolean withK3sDebugging;

    public HelmChartK3sContainerExtension(final boolean withK3sDebugging) {
        this.withK3sDebugging = withK3sDebugging;
    }

    @Override
    public void beforeAll(final @NotNull ExtensionContext context) {
        final var additionalK3sCommands = getAdditionalK3sCommands(context);
        if (additionalK3sCommands.isEmpty()) {
            // if no additional commands are set, we use the cached network and container
            LOG.info("Using cached HelmChartK3sContainer instance");
            final var store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
            networkRef.set((Network) store.computeIfAbsent("network", key -> Network.newNetwork()));
            helmChartK3sContainerRef.set((HelmChartK3sContainer) store.computeIfAbsent("helmChartK3sContainer", key -> {
                LOG.info("Creating cached HelmChartK3sContainer instance");
                final var container = new HelmChartK3sContainer(withK3sDebugging).withNetwork(networkRef.get());
                container.start();
                return container;
            }));
            return;
        }
        // spin up a new network and container with the additional commands
        LOG.info("Using HelmChartK3sContainer instance with additional commands");
        final var network = Network.newNetwork();
        final var container = new HelmChartK3sContainer(withK3sDebugging, additionalK3sCommands).withNetwork(network);
        container.start();
        stopInstances.set(true);
        helmChartK3sContainerRef.set(container);
        networkRef.set(network);
    }

    @Override
    public void afterAll(final @NotNull ExtensionContext context) {
        if (stopInstances.getAndSet(false)) {
            final var container = helmChartK3sContainerRef.getAndSet(null);
            if (container != null) {
                container.stop();
            }
            final var network = networkRef.getAndSet(null);
            if (network != null) {
                network.close();
            }
        } else {
            helmChartK3sContainerRef.set(null);
            networkRef.set(null);
        }
    }

    public @NotNull HelmChartK3sContainer getHelmChartK3sContainer() {
        return helmChartK3sContainerRef.get();
    }

    public @NotNull Network getNetwork() {
        return networkRef.get();
    }

    private @NotNull List<String> getAdditionalK3sCommands(final @NotNull ExtensionContext context) {
        final var testClass = context.getRequiredTestClass();
        if (testClass.isAnnotationPresent(AdditionalK3sCommands.class)) {
            final var additionalK3sCommands = testClass.getAnnotation(AdditionalK3sCommands.class);
            return Arrays.stream(additionalK3sCommands.commands()).toList();
        }
        return List.of();
    }
}
