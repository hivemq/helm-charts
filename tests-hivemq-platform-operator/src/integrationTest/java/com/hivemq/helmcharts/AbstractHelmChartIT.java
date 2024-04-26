package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.testcontainer.LogWaiterUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.awaitility.Awaitility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.hivemq.helmcharts.util.K8sUtil.getNamespaceName;
import static com.hivemq.helmcharts.util.K8sUtil.getOperatorNamespaceName;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractHelmChartIT {

    protected static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    protected static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";

    protected static final @NotNull String PLATFORM_LOG_WAITER_PREFIX = PLATFORM_RELEASE_NAME + "-0";
    protected static final @NotNull String OPERATOR_LOG_WAITER_PREFIX = "hivemq-" + OPERATOR_RELEASE_NAME + "-.*";

    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull HelmChartContainer helmChartContainer;
    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull KubernetesClient client;
    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull LogWaiterUtil logWaiter;
    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull Network network;

    protected final @NotNull String platformNamespace = getNamespaceName(getClass());
    protected final @NotNull String operatorNamespace = getOperatorNamespaceName(getClass());

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseBeforeAll() {
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(3));
        Awaitility.setDefaultTimeout(Duration.ofMinutes(5));
        network = Network.newNetwork();
        helmChartContainer = new HelmChartContainer().withNetwork(network);
        helmChartContainer.start();
        client = helmChartContainer.getKubernetesClient();
        logWaiter = helmChartContainer.getLogWaiter();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseAfterAll() {
        helmChartContainer.stop();
        network.close();
        Awaitility.reset();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseSetUp() throws Exception {
        if (createOperatorNamespace()) {
            helmChartContainer.createNamespace(operatorNamespace);
        }
        if (createPlatformNamespace()) {
            helmChartContainer.createNamespace(platformNamespace);
        }
        if (installOperatorChart()) {
            helmChartContainer.installOperatorChart(OPERATOR_RELEASE_NAME, "--namespace", operatorNamespace);
        }
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseTearDown() throws Exception {
        if (uninstallPlatformChart()) {
            helmChartContainer.uninstallRelease(PLATFORM_RELEASE_NAME, platformNamespace, true);
        }
        if (uninstallOperatorChart()) {
            helmChartContainer.uninstallRelease(OPERATOR_RELEASE_NAME, operatorNamespace, true);
        }
    }

    protected void installOperatorChartAndWaitToBeRunning(final @NotNull String valuesResourceFile) throws Exception {
        installOperatorChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installOperatorChartAndWaitToBeRunning(final @NotNull String... commands) throws Exception {
        helmChartContainer.installOperatorChart(OPERATOR_RELEASE_NAME,
                Stream.concat(Arrays.stream(commands), Stream.of("--namespace", operatorNamespace))
                        .toArray(String[]::new));
        K8sUtil.waitForHiveMQOperatorPodStateRunning(client, operatorNamespace, OPERATOR_RELEASE_NAME);
    }

    protected void installPlatformChartAndWaitToBeRunning(final @NotNull String valuesResourceFile) throws Exception {
        installPlatformChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installPlatformChartAndWaitToBeRunning(
            final @NotNull String... commands) throws Exception {
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                Stream.concat(Arrays.stream(commands), Stream.of("--namespace", platformNamespace))
                        .toArray(String[]::new));
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
    }

    /**
     * Override with {@code return false;} to prevent the creation of the Platform Operator namespace in the
     * {@code @BeforeEach} method.
     */
    protected boolean createOperatorNamespace() {
        return true;
    }

    /**
     * Override with {@code return false;} to prevent the creation of the Platform namespace in the
     * {@code @BeforeEach} method.
     */
    protected boolean createPlatformNamespace() {
        return true;
    }

    /**
     * Override with {@code return false;} to prevent the installation of the Platform Operator chart in the
     * {@code @BeforeEach} method.
     */
    protected boolean installOperatorChart() {
        return true;
    }

    /**
     * Override with {@code return false;} to prevent the uninstallation of the Platform Operator chart in the
     * {@code @AfterEach} method.
     */
    protected boolean uninstallOperatorChart() {
        return true;
    }

    /**
     * Override with {@code return false;} to prevent the uninstallation of the Platform chart in the
     * {@code @AfterEach} method.
     */
    protected boolean uninstallPlatformChart() {
        return true;
    }

    protected final @NotNull CompletableFuture<String> waitForOperatorLog(final @NotNull String log) {
        return logWaiter.waitFor(OPERATOR_LOG_WAITER_PREFIX, log);
    }

    protected final @NotNull CompletableFuture<String> waitForPlatformLog(final @NotNull String log) {
        return logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, log);
    }

    protected final @NotNull CompletableFuture<String> waitForInitAppLog(final @NotNull String log) {
        return logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*\\[HiveMQ Platform Operator Init App\\] " + log);
    }

    @SuppressWarnings("SameParameterValue")
    protected final @NotNull String readResourceFile(final @NotNull String filename) {
        try {
            final var resource = getClass().getResource("/" + filename);
            assertThat(resource).isNotNull();
            return Files.readString(Path.of(resource.toURI()));
        } catch (final Exception e) {
            throw new AssertionError(String.format("Could not read resource file '%s'", filename), e);
        }
    }
}
