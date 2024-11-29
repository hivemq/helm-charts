package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.testcontainer.HelmChartContainerExtension;
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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.hivemq.helmcharts.util.K8sUtil.getNamespaceName;
import static com.hivemq.helmcharts.util.K8sUtil.getOperatorNamespaceName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.awaitility.Durations.TWO_SECONDS;

public abstract class AbstractHelmChartIT {

    protected static final @NotNull String DEFAULT_OPERATOR_NAME_PREFIX = "hivemq";
    protected static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    protected static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    protected static final @NotNull String LEGACY_RELEASE_NAME = "test-hivemq-legacy-platform";

    protected static final int DEFAULT_MQTT_SERVICE_PORT = 1883;
    protected static final @NotNull String DEFAULT_MQTT_SERVICE_NAME =
            "hivemq-" + PLATFORM_RELEASE_NAME + "-mqtt-" + DEFAULT_MQTT_SERVICE_PORT;

    protected static final @NotNull String PLATFORM_LOG_WAITER_PREFIX = PLATFORM_RELEASE_NAME + "-0";
    protected static final @NotNull String OPERATOR_LOG_WAITER_PREFIX =
            String.format("%s-%s-.*", DEFAULT_OPERATOR_NAME_PREFIX, OPERATOR_RELEASE_NAME);

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(AbstractHelmChartIT.class);

    @RegisterExtension
    private static final @NotNull HelmChartContainerExtension HELM_CHART_CONTAINER_EXTENSION =
            new HelmChartContainerExtension();

    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull HelmChartContainer helmChartContainer;
    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull Network network;
    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull KubernetesClient client;
    @SuppressWarnings("NotNullFieldNotInitialized")
    protected static @NotNull LogWaiterUtil logWaiter;

    protected final @NotNull String platformNamespace = getNamespaceName(getClass());
    protected final @NotNull String operatorNamespace = getOperatorNamespaceName(getClass());

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseBeforeAll() {
        Awaitility.setDefaultPollInterval(TWO_SECONDS);
        Awaitility.setDefaultTimeout(FIVE_MINUTES);
        helmChartContainer = HELM_CHART_CONTAINER_EXTENSION.getHelmChartContainer();
        network = HELM_CHART_CONTAINER_EXTENSION.getNetwork();
        client = helmChartContainer.getKubernetesClient();
        logWaiter = helmChartContainer.getLogWaiter();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseAfterAll() {
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
        if (installPlatformOperatorChart()) {
            helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME, "--namespace", operatorNamespace);
        }
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseTearDown() throws Exception {
        try {
            if (uninstallPlatformChart()) {
                helmChartContainer.uninstallRelease(PLATFORM_RELEASE_NAME, platformNamespace, true);
            }
        } finally {
            try {
                if (uninstallPlatformOperatorChart()) {
                    helmChartContainer.uninstallRelease(OPERATOR_RELEASE_NAME, operatorNamespace, true);
                }
            } finally {
                cleanupK3s();
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected void installLegacyOperatorChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installLegacyOperatorChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installLegacyOperatorChartAndWaitToBeRunning(final @NotNull String... commands) throws Exception {
        helmChartContainer.installLegacyOperatorChart(LEGACY_RELEASE_NAME,
                Stream.concat(Arrays.stream(commands), Stream.of("--namespace", operatorNamespace))
                        .toArray(String[]::new));
        K8sUtil.waitForLegacyOperatorPodStateRunning(client, operatorNamespace, LEGACY_RELEASE_NAME);
        K8sUtil.waitForLegacyHiveMQPlatformStateRunning(client, operatorNamespace, LEGACY_RELEASE_NAME);
    }

    protected void installPlatformOperatorChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installPlatformOperatorChartAndWaitToBeRunning(final @NotNull String... commands) throws Exception {
        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME,
                Stream.concat(Arrays.stream(commands), Stream.of("--namespace", operatorNamespace))
                        .toArray(String[]::new));
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, OPERATOR_RELEASE_NAME);
    }

    protected void installPlatformChartAndWaitToBeRunning(final @NotNull String valuesResourceFile) throws Exception {
        installPlatformChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installPlatformChartAndWaitToBeRunning(final @NotNull String... commands) throws Exception {
        installPlatformChart(PLATFORM_RELEASE_NAME, commands);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
    }

    protected void installPlatformChart(final @NotNull String releaseName, final @NotNull String... commands)
            throws Exception {
        helmChartContainer.installPlatformChart(releaseName,
                Stream.concat(Arrays.stream(commands), Stream.of("--namespace", platformNamespace))
                        .toArray(String[]::new));
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
    protected boolean installPlatformOperatorChart() {
        return true;
    }

    /**
     * Override with {@code return false;} to prevent the uninstallation of the Platform Operator chart in the
     * {@code @AfterEach} method.
     */
    protected boolean uninstallPlatformOperatorChart() {
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
        return logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX,
                ".*\\[HiveMQ Platform Operator Init App [A-Z0-9.-]+\\] " + log);
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

    protected static @NotNull String getOperatorName() {
        return String.format("%s-%s", DEFAULT_OPERATOR_NAME_PREFIX, OPERATOR_RELEASE_NAME);
    }

    // free disk space in K3s
    private void cleanupK3s() throws Exception {
        final var pruneContainerResult = helmChartContainer.execInContainer("sh", "-c", """
                for ID in $(ctr container ls --quiet); do \
                TASK=$(ctr task ls | grep $ID); \
                if [ -z "$TASK" ]; then \
                continue; \
                fi; \
                STATUS=$(echo $TASK | awk '{print $3}' | tr -d '[:space:]'); \
                if [ "$STATUS" != "RUNNING" ]; then \
                echo "Removing container $ID (status: $STATUS)"; \
                ctr container rm $ID; \
                fi; \
                done""");
        LOG.info("Removed unused containers:\n{}", pruneContainerResult.getStdout());
        if (pruneContainerResult.getExitCode() != 0) {
            LOG.warn("Error during removal of containers:\n{}", pruneContainerResult.getStderr());
        }
        // the removal will fail if the snapshot has children, so we just print a warning
        final var pruneSnapshots = helmChartContainer.execInContainer("sh", "-c", """
                for ID in $(ctr snapshot ls | awk '{print $1}'); do \
                if [ "$ID" == "KEY" ]; then \
                continue; \
                fi; \
                SNAPSHOT=$(ctr snapshot ls | grep $ID);\
                if echo "$SNAPSHOT" | grep -q "Committed"; then \
                echo "Removing snapshot $ID"; \
                ctr snapshot rm $ID; \
                fi; \
                done""");
        LOG.info("Removed unused snapshots:\n{}", pruneSnapshots.getStdout());
        if (pruneSnapshots.getExitCode() != 0) {
            LOG.warn("Error during removal of snapshots:\n{}", pruneSnapshots.getStderr());
        }
    }
}
