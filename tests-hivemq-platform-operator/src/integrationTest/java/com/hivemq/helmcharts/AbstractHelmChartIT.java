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
import org.testcontainers.containers.Network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.hivemq.helmcharts.util.K8sUtil.MAX_RELEASE_NAME_LENGTH;
import static com.hivemq.helmcharts.util.K8sUtil.getNamespaceName;
import static com.hivemq.helmcharts.util.K8sUtil.getOperatorNamespaceName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.awaitility.Durations.TWO_SECONDS;

@SuppressWarnings("NotNullFieldNotInitialized")
public abstract class AbstractHelmChartIT {

    @RegisterExtension
    private static final @NotNull HelmChartContainerExtension HELM_CHART_CONTAINER_EXTENSION =
            new HelmChartContainerExtension(false);

    protected static final @NotNull String PLATFORM_CRD = "hivemq-platforms.hivemq.com";

    protected static final @NotNull String DEFAULT_OPERATOR_NAME_PREFIX = "hivemq";

    protected static final int DEFAULT_MQTT_SERVICE_PORT = 1883;

    protected static @NotNull HelmChartContainer helmChartContainer;
    protected static @NotNull Network network;
    protected static @NotNull KubernetesClient client;
    protected static @NotNull LogWaiterUtil logWaiter;

    protected final @NotNull String platformNamespace = getNamespaceName(getClass());
    protected final @NotNull String operatorNamespace = getOperatorNamespaceName(getClass());

    protected final @NotNull String releaseBaseName = getReleaseBaseName();
    protected final @NotNull String platformReleaseName = releaseBaseName + "-pf";
    protected final @NotNull String operatorReleaseName = releaseBaseName + "-op";
    protected final @NotNull String legacyReleaseName = releaseBaseName + "-lg";

    protected final @NotNull String defaultMqttServiceName =
            "hivemq-%s-mqtt-%s".formatted(platformReleaseName, DEFAULT_MQTT_SERVICE_PORT);

    protected final @NotNull String platformLogWaiterPrefix = platformReleaseName + "-0";
    protected final @NotNull String operatorLogWaiterPrefix =
            "%s-%s-.*".formatted(DEFAULT_OPERATOR_NAME_PREFIX, operatorReleaseName);

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
        assertThat(platformReleaseName).as("platformReleaseName").hasSizeLessThanOrEqualTo(MAX_RELEASE_NAME_LENGTH);
        assertThat(operatorReleaseName).as("operatorReleaseName").hasSizeLessThanOrEqualTo(MAX_RELEASE_NAME_LENGTH);
        assertThat(legacyReleaseName).as("legacyReleaseName").hasSizeLessThanOrEqualTo(MAX_RELEASE_NAME_LENGTH);
        if (createOperatorNamespace()) {
            helmChartContainer.createNamespace(operatorNamespace);
        }
        if (createPlatformNamespace()) {
            helmChartContainer.createNamespace(platformNamespace);
        }
        if (installPlatformOperatorChart()) {
            helmChartContainer.installPlatformOperatorChart(operatorReleaseName, "--namespace", operatorNamespace);
        }
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseTearDown() throws Exception {
        try {
            if (uninstallPlatformChart()) {
                helmChartContainer.uninstallRelease(platformReleaseName, platformNamespace, true);
            }
        } finally {
            if (uninstallPlatformOperatorChart()) {
                helmChartContainer.uninstallRelease(operatorReleaseName, operatorNamespace, true);
            }
            K8sUtil.deleteCrd(client, PLATFORM_CRD);
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected void installLegacyOperatorChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installLegacyOperatorChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installLegacyOperatorChartAndWaitToBeRunning(final @NotNull String... commands)
            throws Exception {
        helmChartContainer.installLegacyOperatorChart(legacyReleaseName, addDefaultOperatorCommands(commands));
        K8sUtil.waitForLegacyOperatorPodStateRunning(client, operatorNamespace, legacyReleaseName);
        K8sUtil.waitForLegacyHiveMQPlatformStateRunning(client, operatorNamespace, legacyReleaseName);
    }

    protected void installPlatformOperatorChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installPlatformOperatorChartAndWaitToBeRunning(final @NotNull String... commands)
            throws Exception {
        helmChartContainer.installPlatformOperatorChart(operatorReleaseName, addDefaultOperatorCommands(commands));
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, operatorReleaseName);
    }

    protected void installPlatformChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installPlatformChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected void installPlatformChartAndWaitToBeRunning(final @NotNull String... commands) throws Exception {
        installPlatformChart(platformReleaseName, commands);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformReleaseName);
    }

    protected void installPlatformChart(final @NotNull String releaseName, final @NotNull String... commands)
            throws Exception {
        helmChartContainer.installPlatformChart(releaseName, addDefaultPlatformCommands(commands));
    }

    @SuppressWarnings("SameParameterValue")
    protected void upgradePlatformChart(final @NotNull String releaseName, final @NotNull String... commands)
            throws Exception {
        helmChartContainer.upgradePlatformChart(releaseName, addDefaultPlatformCommands(commands));
    }

    /**
     * Override to provide a custom release base name, e.g. when the default truncated name would collide with
     * another test class. The suffixes {@code -pf}, {@code -op}, and {@code -lg} are appended automatically.
     * The returned base name must not exceed {@value K8sUtil#MAX_RELEASE_BASE_NAME_LENGTH} characters.
     */
    protected @NotNull String getReleaseBaseName() {
        return K8sUtil.getReleaseBaseName(getClass());
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
        return logWaiter.waitFor(operatorLogWaiterPrefix, log);
    }

    protected final @NotNull CompletableFuture<String> waitForPlatformLog(final @NotNull String log) {
        return logWaiter.waitFor(platformLogWaiterPrefix, log);
    }

    protected final @NotNull CompletableFuture<String> waitForInitAppLog(final @NotNull String log) {
        return logWaiter.waitFor(platformLogWaiterPrefix,
                ".*\\[HiveMQ Platform Operator Init App [A-Z0-9.-]+\\] " + log);
    }

    @SuppressWarnings("SameParameterValue")
    protected final @NotNull String readResourceFile(final @NotNull String filename) {
        try {
            final var resource = getClass().getResource("/" + filename);
            assertThat(resource).isNotNull();
            return Files.readString(Path.of(resource.toURI()));
        } catch (final Exception e) {
            throw new AssertionError("Could not read resource file '%s'".formatted(filename), e);
        }
    }

    protected @NotNull String getOperatorName() {
        return "%s-%s".formatted(DEFAULT_OPERATOR_NAME_PREFIX, operatorReleaseName);
    }

    private @NotNull String @NotNull [] addDefaultOperatorCommands(final @NotNull String... commands) {
        final var defaultCommands = Stream.of("--namespace", operatorNamespace);
        return Stream.concat(Arrays.stream(commands), defaultCommands).toArray(String[]::new);
    }

    private @NotNull String @NotNull [] addDefaultPlatformCommands(final @NotNull String... commands) {
        final var defaultCommands = Stream.of("--namespace", platformNamespace);
        return Stream.concat(Arrays.stream(commands), defaultCommands).toArray(String[]::new);
    }
}
