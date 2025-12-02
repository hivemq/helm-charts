package com.hivemq.helmcharts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hivemq.helmcharts.testcontainer.HelmChartK3sContainer;
import com.hivemq.helmcharts.testcontainer.HelmChartK3sContainerExtension;
import com.hivemq.helmcharts.testcontainer.LogWaiterUtil;
import com.marcnuri.helm.Helm;
import com.marcnuri.helm.UninstallCommand;
import com.marcnuri.helm.UpgradeCommand;
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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.hivemq.helmcharts.util.K8sUtil.getNamespaceName;
import static com.hivemq.helmcharts.util.K8sUtil.getOperatorNamespaceName;
import static com.marcnuri.helm.UninstallCommand.Cascade.FOREGROUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.awaitility.Durations.TWO_SECONDS;

@SuppressWarnings("NotNullFieldNotInitialized")
public abstract class AbstractHelmChartIT {

    protected @NotNull Chart platformChart;
    protected @NotNull Chart legacyChart;

    protected final @NotNull String platformNamespace = getNamespaceName(getClass());
    protected final @NotNull String operatorNamespace = getOperatorNamespaceName(getClass());

    protected @NotNull UpgradeCommand helmUpgradeLegacyOperator;
    protected @NotNull UninstallCommand helmUninstallLegacyOperator;
    protected @NotNull UpgradeCommand helmUpgradePlatformOperator;
    protected @NotNull UninstallCommand helmUninstallPlatformOperator;
    protected @NotNull UpgradeCommand helmUpgradePlatform;
    protected @NotNull UninstallCommand helmUninstallPlatform;

    protected static @NotNull HelmChartK3sContainer helmChartK3sContainer;
    protected static @NotNull Network network;
    protected static @NotNull KubernetesClient client;
    protected static @NotNull Path kubeConfigPath;
    protected static @NotNull LogWaiterUtil logWaiter;

    protected static final @NotNull String DEFAULT_OPERATOR_NAME_PREFIX = "hivemq";
    protected static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    protected static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    protected static final @NotNull String LEGACY_RELEASE_NAME = "test-hivemq-legacy-platform";

    protected static final int DEFAULT_MQTT_SERVICE_PORT = 1883;
    protected static final @NotNull String DEFAULT_MQTT_SERVICE_NAME =
            "hivemq-%s-mqtt-%s".formatted(PLATFORM_RELEASE_NAME, DEFAULT_MQTT_SERVICE_PORT);

    protected static final @NotNull String PLATFORM_LOG_WAITER_PREFIX = PLATFORM_RELEASE_NAME + "-0";
    protected static final @NotNull String OPERATOR_LOG_WAITER_PREFIX =
            "%s-%s-.*".formatted(DEFAULT_OPERATOR_NAME_PREFIX, OPERATOR_RELEASE_NAME);

    protected static final @NotNull Path VALUES_PATH = Path.of("values");

    private final @NotNull ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    private static final @NotNull String LEGACY_OPERATOR_CHART = "hivemq-operator";
    private static final @NotNull String PLATFORM_CHART = "hivemq-platform";

    @RegisterExtension
    private static final @NotNull HelmChartK3sContainerExtension K3S_CONTAINER_EXTENSION =
            new HelmChartK3sContainerExtension(false);

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseBeforeAll() {
        Awaitility.setDefaultPollInterval(TWO_SECONDS);
        Awaitility.setDefaultTimeout(FIVE_MINUTES);
        helmChartK3sContainer = K3S_CONTAINER_EXTENSION.getHelmChartK3sContainer();
        network = K3S_CONTAINER_EXTENSION.getNetwork();
        client = helmChartK3sContainer.getKubernetesClient();
        logWaiter = helmChartK3sContainer.getLogWaiter();
        kubeConfigPath = helmChartK3sContainer.getKubeConfigPath();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseAfterAll() {
        Awaitility.reset();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseSetUp() throws Exception {
        legacyChart = getLocalChart(LEGACY_OPERATOR_CHART);
        platformChart = getLocalChart(PLATFORM_CHART);
        helmUpgradeLegacyOperator = Helm.upgrade("../charts/hivemq-operator")
                .withName(LEGACY_RELEASE_NAME)
                .withNamespace(operatorNamespace)
                .withKubeConfig(kubeConfigPath)
                .install()
                .atomic()
                .waitReady()
                .debug();
        helmUninstallLegacyOperator = Helm.uninstall(LEGACY_RELEASE_NAME)
                .withNamespace(operatorNamespace)
                .withKubeConfig(kubeConfigPath)
                .withCascade(FOREGROUND)
                .debug();
        helmUpgradePlatformOperator = Helm.upgrade("../charts/hivemq-platform-operator")
                .withName(OPERATOR_RELEASE_NAME)
                .withNamespace(operatorNamespace)
                .withKubeConfig(kubeConfigPath)
                .set("image.repository", "host.docker.internal/hivemq")
                .set("image.tag", "snapshot")
                .set("logLevel", "DEBUG")
                .set("resources.cpu", "512m")
                .install()
                .atomic()
                .waitReady()
                .debug();
        helmUninstallPlatformOperator = Helm.uninstall(OPERATOR_RELEASE_NAME)
                .withNamespace(operatorNamespace)
                .withKubeConfig(kubeConfigPath)
                .withCascade(FOREGROUND)
                .debug();
        helmUpgradePlatform = Helm.upgrade("../charts/hivemq-platform")
                .withName(PLATFORM_RELEASE_NAME)
                .withNamespace(platformNamespace)
                .withKubeConfig(kubeConfigPath)
                .set("image.repository", "host.docker.internal/hivemq")
                .set("image.tag", "latest")
                .set("nodes.resources.cpu", "512m")
                .set("nodes.replicaCount", "1")
                .install()
                .atomic()
                .waitReady()
                .debug();
        helmUninstallPlatform = Helm.uninstall(PLATFORM_RELEASE_NAME)
                .withNamespace(platformNamespace)
                .withKubeConfig(kubeConfigPath)
                .withCascade(FOREGROUND)
                .debug();
        if (createOperatorNamespace()) {
            helmChartK3sContainer.createNamespace(operatorNamespace);
        }
        if (createPlatformNamespace()) {
            helmChartK3sContainer.createNamespace(platformNamespace);
        }
        if (installPlatformOperatorChart()) {
            helmUpgradePlatformOperator.call();
        }
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseTearDown() {
        try {
            if (uninstallPlatformChart()) {
                helmUninstallPlatform.call();
                helmChartK3sContainer.deleteNamespace(platformNamespace);
            }
        } finally {
            if (uninstallPlatformOperatorChart()) {
                helmUninstallPlatformOperator.call();
                helmChartK3sContainer.deleteNamespace(operatorNamespace);
            }
        }
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
            throw new AssertionError("Could not read resource file '%s'".formatted(filename), e);
        }
    }

    protected @NotNull Chart getPreviousPlatformChart() {
        final var description = "*.%s*".formatted(platformChart.getDescription());
        Helm.repo().add().withName("hivemq").withUrl(URI.create("https://hivemq.github.io/helm-charts")).call();
        final var searchResults = Helm.search().repo().withKeyword(description).call();
        return searchResults.stream()
                .filter(result -> result.getChartVersion() != null)
                .filter(result -> {
                    final var chartVersion = Version.parse(result.getChartVersion());
                    return !Objects.equals(chartVersion, platformChart.getChartVersion());
                })
                .map(Chart::of)
                .max(Comparator.comparing(Chart::getChartVersion))
                .orElseThrow(() -> new IllegalStateException("No previous chart version found"));
    }

    protected static @NotNull String getOperatorName() {
        return "%s-%s".formatted(DEFAULT_OPERATOR_NAME_PREFIX, OPERATOR_RELEASE_NAME);
    }

    private @NotNull Chart getLocalChart(final @NotNull String chart) throws Exception {
        final var chartFile = Path.of("../charts/%s".formatted(chart)).resolve("Chart.yaml").toFile();
        if (!chartFile.exists()) {
            throw new IllegalArgumentException("Chart.yaml not found at: " + chartFile.getAbsolutePath());
        }
        return objectMapper.readValue(chartFile, Chart.class);
    }
}
