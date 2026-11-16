package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class HelmChartContainerExtension implements BeforeAllCallback, AfterAllCallback {

    // a stall breaks the whole CI runner, its DNS resolution included, so it is recorded for the rest of the job in the
    // runner temp directory, which also reaches the new JVM of each test-retry round
    private static final @Nullable Path STALL_FILE = Optional.ofNullable(System.getenv("RUNNER_TEMP"))
            .map(runnerTemp -> Path.of(runnerTemp, "k3s-stall-detected"))
            .orElse(null);

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmChartContainerExtension.class);

    private final @NotNull AtomicBoolean stopInstances = new AtomicBoolean(false);
    private final @NotNull AtomicReference<HelmChartContainer> helmChartContainerRef = new AtomicReference<>();
    private final @NotNull AtomicReference<Network> networkRef = new AtomicReference<>();

    private final boolean withK3sDebugging;

    public HelmChartContainerExtension(final boolean withK3sDebugging) {
        this.withK3sDebugging = withK3sDebugging;
    }

    @Override
    public void beforeAll(final @NotNull ExtensionContext context) {
        // once a stall is recorded, any further test (a test-retry rerun on CI, or the next test class) would run on the
        // stalled runtime again; fail fast before using or starting a container
        if ("true".equals(System.getenv("CI_RUN")) && hasStalledOnThisRunner()) {
            throw new IllegalStateException(
                    "K3s containerd runtime already stalled in this run; failing fast without starting a new container.");
        }
        final var additionalK3sCommands = getAdditionalK3sCommands(context);
        if (additionalK3sCommands.isEmpty()) {
            // if no additional commands are set, we use the cached network and container
            LOG.info("Using cached HelmChartContainer instance");
            final var store = context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL);
            final var cachedNetwork =
                    (CachedNetwork) store.computeIfAbsent("network", _ -> new CachedNetwork(Network.newNetwork()));
            networkRef.set(cachedNetwork.network());
            helmChartContainerRef.set((HelmChartContainer) store.computeIfAbsent("helmChartContainer", _ -> {
                LOG.info("Creating cached HelmChartContainer instance");
                final var container = new HelmChartContainer(withK3sDebugging).withNetwork(networkRef.get());
                container.start();
                return container;
            }));
            return;
        }
        // spin up a new network and container with the additional commands
        LOG.info("Using HelmChartContainer instance with additional commands");
        final var network = Network.newNetwork();
        final var container = new HelmChartContainer(withK3sDebugging, additionalK3sCommands).withNetwork(network);
        container.start();
        stopInstances.set(true);
        helmChartContainerRef.set(container);
        networkRef.set(network);
    }

    @Override
    public void afterAll(final @NotNull ExtensionContext context) {
        recordStallOnThisRunner();
        if (stopInstances.getAndSet(false)) {
            final var container = helmChartContainerRef.getAndSet(null);
            if (container != null) {
                container.stop();
            }
            final var network = networkRef.getAndSet(null);
            if (network != null) {
                closeUnlessStalled(network);
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

    private static boolean hasStalledOnThisRunner() {
        return K3sRuntimeStallDetector.hasDetectedStall() || (STALL_FILE != null && Files.exists(STALL_FILE));
    }

    private static void recordStallOnThisRunner() {
        if (STALL_FILE == null || !K3sRuntimeStallDetector.hasDetectedStall()) {
            return;
        }
        try {
            Files.writeString(STALL_FILE, "");
        } catch (final IOException e) {
            LOG.warn("Could not record the K3s runtime stall in {}", STALL_FILE, e);
        }
    }

    private static void closeUnlessStalled(final @NotNull Network network) {
        // removing the network goes through docker-java, which resolves the docker host on every new connection, a lookup
        // the stall can wedge; a stalled run leaves the network to Ryuk instead of blocking on its removal
        if (!K3sRuntimeStallDetector.hasDetectedStall()) {
            network.close();
        }
    }

    // the root store closes its values when the test run ends, so the cached network takes the same stall check
    private record CachedNetwork(@NotNull Network network) implements AutoCloseable {

        @Override
        public void close() {
            closeUnlessStalled(network);
        }
    }
}
