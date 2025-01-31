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

public class HelmChartContainerExtension implements BeforeAllCallback, AfterAllCallback {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmChartContainerExtension.class);

    private final @NotNull AtomicBoolean stopInstances = new AtomicBoolean(false);
    private final @NotNull AtomicReference<HelmChartContainer> helmChartContainerRef = new AtomicReference<>();
    private final @NotNull AtomicReference<Network> networkRef = new AtomicReference<>();

    @Override
    public void beforeAll(final @NotNull ExtensionContext context) {
        final var additionalK3sCommands = getAdditionalK3sCommands(context);
        if (additionalK3sCommands.isEmpty()) {
            // if no additional commands are set, we use the cached network and container
            LOG.info("Using cached HelmChartContainer instance");
            final var store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
            networkRef.set((Network) store.getOrComputeIfAbsent("network", key -> Network.newNetwork()));
            helmChartContainerRef.set((HelmChartContainer) store.getOrComputeIfAbsent("helmChartContainer", key -> {
                LOG.info("Creating cached HelmChartContainer instance");
                final var container = new HelmChartContainer().withNetwork(networkRef.get());
                container.start();
                return container;
            }));
            return;
        }
        // spin up a new network and container with the additional commands
        LOG.info("Using HelmChartContainer instance with additional commands");
        final var network = Network.newNetwork();
        final var container = new HelmChartContainer(additionalK3sCommands).withNetwork(network);
        container.start();
        stopInstances.set(true);
        helmChartContainerRef.set(container);
        networkRef.set(network);
    }

    @Override
    public void afterAll(final @NotNull ExtensionContext context) {
        if (stopInstances.getAndSet(false)) {
            final var container = helmChartContainerRef.getAndSet(null);
            if (container != null) {
                container.stop();
            }
            final var network = networkRef.getAndSet(null);
            if (network != null) {
                network.close();
            }
        } else {
            helmChartContainerRef.set(null);
            networkRef.set(null);
        }
    }

    public @NotNull HelmChartContainer getHelmChartContainer() {
        return helmChartContainerRef.get();
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
