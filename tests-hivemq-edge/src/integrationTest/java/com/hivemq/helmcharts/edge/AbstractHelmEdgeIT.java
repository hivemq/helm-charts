package com.hivemq.helmcharts.edge;

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

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.hivemq.helmcharts.util.K8sUtil.getNamespaceName;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.awaitility.Durations.TWO_SECONDS;

/**
 * Base class for HiveMQ Edge Helm chart integration tests. Edge ships as a plain StatefulSet + Service (no operator,
 * no CRD), so this base class only manages a namespace and the chart release lifecycle.
 */
@SuppressWarnings("NotNullFieldNotInitialized")
public abstract class AbstractHelmEdgeIT {

    @RegisterExtension
    private static final @NotNull HelmChartContainerExtension HELM_CHART_CONTAINER_EXTENSION =
            new HelmChartContainerExtension(false);

    protected static final @NotNull String EDGE_RELEASE_NAME = "test-hivemq-edge";
    protected static final @NotNull String EDGE_POD_NAME = "hivemq-" + EDGE_RELEASE_NAME + "-0";

    protected static @NotNull HelmChartContainer helmChartContainer;
    protected static @NotNull KubernetesClient client;
    protected static @NotNull LogWaiterUtil logWaiter;

    protected final @NotNull String edgeNamespace = getNamespaceName(getClass());

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseBeforeAll() {
        Awaitility.setDefaultPollInterval(TWO_SECONDS);
        Awaitility.setDefaultTimeout(FIVE_MINUTES);
        helmChartContainer = HELM_CHART_CONTAINER_EXTENSION.getHelmChartContainer();
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
    final void baseSetUp() {
        helmChartContainer.createNamespace(edgeNamespace);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseTearDown() throws Exception {
        helmChartContainer.uninstallRelease(EDGE_RELEASE_NAME, edgeNamespace, true);
    }

    protected void installEdgeChartAndWaitToBeRunning(final @NotNull String... commands) throws Exception {
        helmChartContainer.installEdgeChart(EDGE_RELEASE_NAME, addDefaultEdgeCommands(commands));
        K8sUtil.waitForHiveMQEdgePodStateRunning(client, edgeNamespace, EDGE_RELEASE_NAME);
    }

    /**
     * Returns a future that completes when the Edge pod logs the standard startup-complete message. The Edge chart
     * has only a {@code livenessProbe} (no {@code readinessProbe}), so the K8s {@code ready} status flips before the
     * application has finished booting and bound its listeners. Tests that need to interact with Edge (e.g. opening
     * an MQTT connection) should wait on this future after {@link #installEdgeChartAndWaitToBeRunning} returns.
     */
    protected final @NotNull CompletableFuture<String> waitForEdgeStartupLog() {
        return logWaiter.waitFor(EDGE_POD_NAME, ".*Started HiveMQ Edge in.*");
    }

    /**
     * Returns a future that completes when the Edge pod logs its version banner ({@code HiveMQ Edge Version: X}) for
     * the given expected version. Use this to assert that the deployed pod is running the version declared in
     * {@code libs.versions.toml}.
     */
    protected final @NotNull CompletableFuture<String> waitForEdgeVersionLog(final @NotNull String version) {
        return logWaiter.waitFor(EDGE_POD_NAME, ".*HiveMQ Edge Version: " + Pattern.quote(version) + ".*");
    }

    private @NotNull String @NotNull [] addDefaultEdgeCommands(final @NotNull String... commands) {
        final var defaultCommands = Stream.of("--namespace", edgeNamespace);
        return Stream.concat(Arrays.stream(commands), defaultCommands).toArray(String[]::new);
    }
}
