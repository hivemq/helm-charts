package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.testcontainer.HelmChartContainerExtension;
import com.hivemq.helmcharts.testcontainer.K3sStallFailureExtension;
import com.hivemq.helmcharts.testcontainer.LogWaiterUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.PodUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.awaitility.Awaitility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hivemq.helmcharts.util.K8sUtil.MAX_RELEASE_NAME_LENGTH;
import static com.hivemq.helmcharts.util.K8sUtil.getNamespaceName;
import static com.hivemq.helmcharts.util.K8sUtil.getOperatorNamespaceName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.awaitility.Durations.TWO_SECONDS;

@SuppressWarnings("NotNullFieldNotInitialized")
@ExtendWith(K3sStallFailureExtension.class)
public abstract class AbstractHelmChartIT {

    @RegisterExtension
    private static final @NotNull HelmChartContainerExtension HELM_CHART_CONTAINER_EXTENSION =
            new HelmChartContainerExtension(false);

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(AbstractHelmChartIT.class);

    protected static final @NotNull String PLATFORM_CRD = "hivemq-platforms.hivemq.com";
    protected static final @NotNull String DEFAULT_OPERATOR_NAME_PREFIX = "hivemq";
    protected static final @NotNull String CUSTOM_PLATFORM_IMAGE_VARIANT =
            System.getProperty("custom.platform.image.variant", "");
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
    protected final void installLegacyOperatorChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installLegacyOperatorChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected final void installLegacyOperatorChartAndWaitToBeRunning(final @NotNull String... commands)
            throws Exception {
        helmChartContainer.installLegacyOperatorChart(legacyReleaseName, addDefaultOperatorCommands(commands));
        K8sUtil.waitForLegacyOperatorPodStateRunning(client, operatorNamespace, legacyReleaseName);
        K8sUtil.waitForLegacyHiveMQPlatformStateRunning(client, operatorNamespace, legacyReleaseName);
    }

    protected final void installPlatformOperatorChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected final void installPlatformOperatorChartAndWaitToBeRunning(final @NotNull String... commands)
            throws Exception {
        helmChartContainer.installPlatformOperatorChart(operatorReleaseName, addDefaultOperatorCommands(commands));
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, operatorReleaseName);
    }

    protected final void installPlatformChartAndWaitToBeRunning(final @NotNull String valuesResourceFile)
            throws Exception {
        installPlatformChartAndWaitToBeRunning("-f", valuesResourceFile);
    }

    protected final void installPlatformChartAndWaitToBeRunning(final @NotNull String... commands) throws Exception {
        installPlatformChart(platformReleaseName, commands);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformReleaseName);
        if (!CUSTOM_PLATFORM_IMAGE_VARIANT.isEmpty()) {
            logCustomPlatformImageRuntime();
        }
    }

    protected final void installPlatformChart(final @NotNull String releaseName, final @NotNull String... commands)
            throws Exception {
        helmChartContainer.installPlatformChart(releaseName, addDefaultPlatformCommands(commands));
    }

    @SuppressWarnings("SameParameterValue")
    protected final void upgradePlatformChart(final @NotNull String releaseName, final @NotNull String... commands)
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

    protected final @NotNull String getOperatorName() {
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

    private void logCustomPlatformImageRuntime() {
        final var podName = platformReleaseName + "-0";
        final var osRelease = parseOsRelease(executeInPlatformPod(podName, "cat", "/etc/os-release"));
        final var osDetails = Stream.of("ID", "ID_LIKE", "PLATFORM_ID", "VERSION_CODENAME")
                .filter(osRelease::containsKey)
                .map(key -> key + "=" + osRelease.get(key))
                .toList();
        final var operatingSystem = osRelease.getOrDefault("PRETTY_NAME", "an unknown operating system") +
                (osDetails.isEmpty() ? "" : " (" + String.join(", ", osDetails) + ")");
        final var javaVersion = executeInPlatformPod(podName, "java", "-version");
        LOG.info("Custom platform image variant '{}' runs on {}:\n{}",
                CUSTOM_PLATFORM_IMAGE_VARIANT,
                operatingSystem,
                javaVersion);
    }

    private @NotNull String executeInPlatformPod(final @NotNull String podName, final @NotNull String... command) {
        final var execResult = PodUtil.execute(client, platformNamespace, podName, "hivemq", command);
        try {
            if (!execResult.await(15, TimeUnit.SECONDS)) {
                LOG.warn("Could not execute '{}' in pod '{}'", String.join(" ", command), podName);
                return "";
            }
            // java -version prints to stderr, so both streams are returned
            return Stream.of(execResult.getOutput(), execResult.getError())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
        } finally {
            execResult.close();
        }
    }

    private static @NotNull Map<String, String> parseOsRelease(final @NotNull String osRelease) {
        return osRelease.lines()
                .map(line -> line.split("=", 2))
                .filter(keyValue -> keyValue.length == 2)
                .collect(Collectors.toMap(keyValue -> keyValue[0],
                        keyValue -> keyValue[1].replace("\"", ""),
                        (first, _) -> first));
    }
}
