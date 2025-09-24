package com.hivemq.helmcharts.testcontainer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.dockerjava.api.DockerClient;
import com.hivemq.helmcharts.Chart;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.hivemq.helmcharts.util.NginxUtil.NGINX_CONTAINER_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_MINUTES;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;

public class HelmChartContainer extends K3sContainer implements AutoCloseable {

    public static final @NotNull String MANIFEST_FILES = "manifests";

    private static final @NotNull DockerImageName K3S_DOCKER_IMAGE =
            OciImages.getImageName("hivemq/helm-charts").asCompatibleSubstituteFor("rancher/k3s");
    private static final @NotNull String LEGACY_OPERATOR_CHART = "hivemq-operator";
    private static final @NotNull String PLATFORM_OPERATOR_CHART = "hivemq-platform-operator";
    private static final @NotNull String PLATFORM_CHART = "hivemq-platform";
    private static final @NotNull String LOG_PREFIX_EVENT = "EVENT";
    private static final @NotNull String LOG_PREFIX_POD = "POD";
    private static final @NotNull String LOG_PREFIX_K3S = "K3S";
    private static final @NotNull Set<String> LOG_WATCHER_CONTAINERS =
            Set.of("hivemq", "hivemq-platform-operator", "operator", "consul-template", NGINX_CONTAINER_NAME);
    private static final @NotNull Pattern LOGBACK_DATE_PREFIX =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3} (.*)");
    private static final @NotNull String POD_CPU_LIMIT = "512m";

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmChartContainer.class);

    private final @NotNull ExecutorService executorService = Executors.newCachedThreadPool();
    private final @NotNull Map<String, Watch> watches = new ConcurrentHashMap<>();
    private final @NotNull Map<String, LogWatch> logWatches = new ConcurrentHashMap<>();
    private final @NotNull LogWaiterUtil logWaiter = new LogWaiterUtil();
    private @Nullable Chart platformChart;
    private @Nullable Chart legacyChart;

    private @Nullable KubernetesClient client;

    public HelmChartContainer(final boolean withK3sDebugging) {
        this(withK3sDebugging, List.of());
    }

    public HelmChartContainer(final boolean withK3sDebugging, final @NotNull List<String> additionalCommands) {
        super(K3S_DOCKER_IMAGE);
        super.withClasspathResourceMapping("values", "/files/", BindMode.READ_ONLY);
        super.withCopyFileToContainer(MountableFile.forHostPath("../charts/" + LEGACY_OPERATOR_CHART),
                "/charts/" + LEGACY_OPERATOR_CHART);
        super.withCopyFileToContainer(MountableFile.forHostPath("../charts/" + PLATFORM_OPERATOR_CHART),
                "/charts/" + PLATFORM_OPERATOR_CHART);
        super.withCopyFileToContainer(MountableFile.forHostPath("../charts/" + PLATFORM_CHART),
                "/charts/" + PLATFORM_CHART);
        super.withCopyFileToContainer(MountableFile.forHostPath("../" + MANIFEST_FILES), "/" + MANIFEST_FILES);
        super.withCopyFileToContainer(MountableFile.forHostPath("../scripts/test.sh"), "/bin/test.sh");

        super.withCopyToContainer(Transferable.of(getRegistriesContent()), "/etc/rancher/k3s/registries.yaml");
        super.withExtraHost("host.docker.internal", "host-gateway");

        super.withStartupCheckStrategy(new K3sReadyStartupCheckStrategy(this));
        super.withLogConsumer(new K3sLogConsumer(LOG).withPrefix(LOG_PREFIX_K3S).withDebugging(withK3sDebugging));
        super.withLogConsumer(outputFrame -> logWaiter.accept(LOG_PREFIX_K3S, outputFrame.getUtf8String().trim()));
        final var k3sCommands = new ArrayList<>(List.of("server",
                "--etcd-arg=unsafe-no-fsync",
                "--etcd-arg=snapshot-count=10000",
                "--etcd-arg=auto-compaction-mode=revision",
                "--etcd-arg=auto-compaction-retention=1000000",
                "--kube-apiserver-arg=etcd-compaction-interval=0s",
                "--tls-san=" + getHost()));
        if (Objects.equals(System.getenv("K8S_VERSION_TYPE"), "LATEST")) {
            k3sCommands.add("--disable-default-registry-endpoint");
        }
        if (!additionalCommands.isEmpty()) {
            LOG.debug("Starting K3s container with additional commands: {}", additionalCommands);
            k3sCommands.addAll(additionalCommands);
        }
        if (withK3sDebugging) {
            LOG.debug("Starting K3s container with --debug");
            k3sCommands.add("--debug");
            k3sCommands.add("-v");
            k3sCommands.add("4");
        }
        super.withCommand(k3sCommands.toArray(new String[0]));
    }

    public static @NotNull String resolveLocalImage(final @NotNull String imageName) {
        final var ociImage = OciImages.getImageName(imageName);
        return "host.docker.internal/%s:%s".formatted(ociImage.getRepository(), ociImage.getVersionPart());
    }

    public @NotNull Chart getLegacyOperatorChart() {
        if (legacyChart != null) {
            return legacyChart;
        }
        legacyChart = getLocalChart(LEGACY_OPERATOR_CHART);
        return legacyChart;
    }

    public @NotNull Chart getPlatformChart() {
        if (platformChart != null) {
            return platformChart;
        }
        platformChart = getLocalChart(PLATFORM_CHART);
        return platformChart;
    }

    public @NotNull Chart getPreviousPlatformChart() throws Exception {
        final var currentChart = getPlatformChart();
        final var regex = "*.%s*".formatted(currentChart.getDescription());
        final var platformCharts = executeHelmSearchCommand("hivemq/hivemq-platform",
                Stream.of("--versions", "--regexp", regex, "--output", "yaml"));
        final var objectMapper = new ObjectMapper(new YAMLFactory());
        final var platformChartsList = objectMapper.readValue(platformCharts.replaceAll("app_version", "appVersion"),
                new TypeReference<List<Chart>>() {
                });//
        return platformChartsList.stream()
                .filter(chart -> chart.getVersion() != null)
                .filter(chart -> !Objects.equals(chart.getVersion(), currentChart.getVersion()))
                .max(Comparator.comparing(Chart::getVersion))
                .orElseThrow();
    }

    @Override
    public final void start() {
        LOG.info("Starting HelmChartContainer...");
        super.start();
        addHelmRepo("hivemq", "https://hivemq.github.io/helm-charts");
        final var client = getKubernetesClient();
        watches.put("events", client.events().v1().events().inAnyNamespace().watch(new EventWatcher(client)));
        watches.put("pods", client.pods().inAnyNamespace().watch(new PodWatcher()));
        this.client = client;
        try {
            final var configPath = Files.createTempFile("kubeconfig-", ".yaml").toAbsolutePath();
            configPath.toFile().deleteOnExit();
            Files.writeString(configPath, getKubeConfigYaml());
            LOG.info("Saved kubeconfig file on {}", configPath);
        } catch (final IOException e) {
            LOG.error("Could not save kubeconfig.yaml to temporary file", e);
        }
        LOG.info("HelmChartContainer is started");
    }

    @Override
    public final void stop() {
        LOG.info("Stopping HelmChartContainer...");
        logWatches.values().forEach(LogWatch::close);
        watches.values().forEach(Watch::close);
        final var client = this.client;
        if (client != null) {
            client.close();
            this.client = null;
        }
        executorService.shutdownNow();
        super.stop();
        LOG.info("HelmChartContainer is stopped");
    }

    @Override
    public void close() {
        stop();
    }

    public @NotNull HelmChartContainer withNetwork(final @NotNull Network network) {
        super.withNetwork(network);
        return this;
    }

    public void createNamespace(final @NotNull String name) {
        LOG.info("Creating namespace '{}'...", name);
        final var client = getKubernetesClient();
        final var namespace = client.namespaces()
                .resource(new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build())
                .create();
        assertThat(namespace).isNotNull();
        LOG.info("Namespace '{}' created", name);
    }

    public void deleteNamespace(final @NotNull String name) {
        LOG.info("Deleting namespace '{}'...", name);
        final var client = getKubernetesClient();
        final var namespaceDeleted = client.namespaces().withName(name).informOnCondition(List::isEmpty);
        assertThat(client.namespaces().withName(name).delete()).isNotEmpty();
        await().atMost(FIVE_MINUTES).pollInterval(ONE_HUNDRED_MILLISECONDS).until(namespaceDeleted::isDone);
        LOG.info("Namespace '{}' deleted", name);
    }

    public @NotNull LogWaiterUtil getLogWaiter() {
        return logWaiter;
    }

    public synchronized @NotNull KubernetesClient getKubernetesClient() {
        if (client == null) {
            final var config = Config.fromKubeconfig(getKubeConfigYaml());
            client = new KubernetesClientBuilder().withConfig(config).build();
        }
        return client;
    }

    public void addHelmRepo(final @NotNull String name, final @NotNull String url) {
        // helm --kubeconfig /etc/rancher/k3s/k3s.yaml repo add <name> <url>
        final var helmCommandList =
                new ArrayList<>(List.of("helm", "--kubeconfig", "/etc/rancher/k3s/k3s.yaml", "repo", "add", name, url));
        LOG.debug("Executing helm command: {}", String.join(" ", helmCommandList));
        try {
            final var execResult = execInContainer(helmCommandList.toArray(new String[0]));
            assertThat(execResult.getStderr()).as("stdout: %s\nstderr: %s",
                    execResult.getStdout(),
                    execResult.getStderr()).isEmpty();
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    public void installChart(
            final @NotNull String release,
            final @NotNull String chart,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("install", release, chart, false, Stream.of(additionalCommands), true);
    }

    public void installLegacyOperatorChart(
            final @NotNull String releaseName,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("install",
                releaseName,
                resolveChartLocation(LEGACY_OPERATOR_CHART, true),
                true,
                Stream.of(additionalCommands),
                true);
    }

    public void installPlatformOperatorChart(
            final @NotNull String releaseName,
            final boolean withLocalCharts,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("install",
                releaseName,
                resolveChartLocation(PLATFORM_OPERATOR_CHART, withLocalCharts),
                withLocalCharts,
                Stream.concat(getOperatorFixedValues(withLocalCharts), Stream.of(additionalCommands)),
                true);
    }

    public void installPlatformOperatorChart(
            final @NotNull String releaseName,
            final @NotNull String... additionalCommands) throws Exception {
        installPlatformOperatorChart(releaseName, true, additionalCommands);
    }

    public void installPlatformChart(
            final @NotNull String releaseName,
            final boolean withLocalCharts,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("install",
                releaseName,
                resolveChartLocation(PLATFORM_CHART, withLocalCharts),
                withLocalCharts,
                Stream.concat(getPlatformFixedValues(withLocalCharts), Stream.of(additionalCommands)),
                true);
    }

    public void installPlatformChart(final @NotNull String releaseName, final @NotNull String... additionalCommands)
            throws Exception {
        installPlatformChart(releaseName, true, additionalCommands);
    }

    public void uninstallRelease(
            final @NotNull String releaseName,
            final @NotNull String namespace,
            final @NotNull String... additionalCommands) throws Exception {
        uninstallRelease(releaseName, namespace, false, additionalCommands);
    }

    public void uninstallRelease(
            final @NotNull String releaseName,
            final @NotNull String namespace,
            final boolean deleteNamespace,
            final @NotNull String... additionalCommands) throws Exception {
        LOG.info("Uninstalling release '{}' in namespace '{}'...", releaseName, namespace);
        try {
            executeHelmCommand("uninstall",
                    releaseName,
                    null,
                    true,
                    Stream.concat(Stream.of("--cascade", "foreground", "--namespace", namespace),
                            Stream.of(additionalCommands)),
                    true);
        } finally {
            if (deleteNamespace) {
                deleteNamespace(namespace);
            }
            LOG.info("Release '{}' in namespace '{}' is uninstalled", releaseName, namespace);
        }
    }

    public void upgradePlatformOperatorChart(
            final @NotNull String releaseName,
            final boolean withLocalCharts,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("upgrade",
                releaseName,
                resolveChartLocation(PLATFORM_OPERATOR_CHART, withLocalCharts),
                withLocalCharts,
                Stream.concat(getOperatorFixedValues(withLocalCharts), Stream.of(additionalCommands)),
                true);
    }

    public void upgradePlatformOperatorChart(
            final @NotNull String releaseName,
            final @NotNull String... additionalCommands) throws Exception {
        upgradePlatformOperatorChart(releaseName, true, additionalCommands);
    }

    public void upgradePlatformChart(
            final @NotNull String releaseName,
            final boolean withLocalCharts,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("upgrade",
                releaseName,
                resolveChartLocation(PLATFORM_CHART, withLocalCharts),
                withLocalCharts,
                Stream.concat(getPlatformFixedValues(withLocalCharts), Stream.of(additionalCommands)),
                true);
    }

    public void upgradePlatformChart(final @NotNull String releaseName, final @NotNull String... additionalCommands)
            throws Exception {
        upgradePlatformChart(releaseName, true, additionalCommands);
    }

    private @NotNull String executeHelmCommand(
            final @NotNull String helmCommand,
            final @NotNull String releaseName,
            final @Nullable String chartName,
            final boolean withLocalCharts,
            final @NotNull Stream<String> additionalCommands,
            final boolean debugOnFailure) throws Exception {
        // helm --kubeconfig /etc/rancher/k3s/k3s.yaml <install|upgrade> test-operator /chart/hivemq-platform-operator --wait --timeout 5m0s
        final var helmCommandList = new ArrayList<>(List.of("helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                helmCommand,
                releaseName,
                chartName != null ? chartName : "",
                "--wait",
                "--timeout",
                "5m0s"));
        final var additionalCommandsList = additionalCommands.toList();
        helmCommandList.addAll(additionalCommandsList);
        if (chartName != null && withLocalCharts) {
            // helm dependency update /chart
            final var outUpdate = execInContainer("helm", "dependency", "update", chartName);
            assertThat(outUpdate.getStderr()).as("stdout: %s\nstderr: %s", outUpdate.getStdout(), outUpdate.getStderr())
                    .isEmpty();
        }

        LOG.debug("Executing helm command: {}", String.join(" ", helmCommandList));
        final var execResult = execInContainer(helmCommandList.toArray(new String[0]));
        assertThat(execResult.getStdout()).as(() -> describeHelmCommand(execResult,
                helmCommandList,
                helmCommand,
                releaseName,
                chartName,
                withLocalCharts,
                additionalCommandsList.stream(),
                debugOnFailure)).isNotEmpty();

        return execResult.getStdout();
    }

    @SuppressWarnings("SameParameterValue")
    private @NotNull String executeHelmSearchCommand(
            final @NotNull String chartName,
            final @NotNull Stream<String> additionalCommands)
            throws Exception {
        // helm --kubeconfig /etc/rancher/k3s/k3s.yaml search repo <repo|chart>
        final var helmCommandList = new ArrayList<>(List.of("helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                "search",
                "repo",
                chartName));
        helmCommandList.addAll(additionalCommands.toList());

        LOG.debug("Executing helm command: {}", String.join(" ", helmCommandList));
        final var execResult = execInContainer(helmCommandList.toArray(new String[0]));
        assertThat(execResult.getStderr()).as("stdout: %s\nstderr: %s", execResult.getStdout(), execResult.getStderr())
                .isEmpty();
        return execResult.getStdout();
    }

    private @NotNull String describeHelmCommand(
            final @NotNull ExecResult execDeploy,
            final @NotNull List<String> helmCommandList,
            final @NotNull String helmCommand,
            final @NotNull String releaseName,
            final @Nullable String chartName,
            final boolean withLocalCharts,
            final @NotNull Stream<String> additionalCommands,
            final boolean debugOnFailure) {
        try {
            return "Helm command: %s\nstdout: %s\nstderr: %s\nyaml:\n%s".formatted(helmCommandList,
                    execDeploy.getStdout(),
                    execDeploy.getStderr(),
                    // execute Helm command with --debug and --dry-run to get additional error information
                    debugOnFailure ?
                            executeHelmCommand(helmCommand,
                                    releaseName,
                                    chartName,
                                    withLocalCharts,
                                    Stream.concat(Stream.of("--debug", "--dry-run"), additionalCommands),
                                    false) :
                            "n/a");
        } catch (final Exception e) {
            return "Could not describe Helm command: " + e;
        }
    }

    private @NotNull Chart getLocalChart(final @NotNull String chart) {
        return copyFileFromContainer("/charts/" + chart + "/Chart.yaml", inputStream -> {
            final var objectMapper = new ObjectMapper(new YAMLFactory());
            return objectMapper.readValue(inputStream, Chart.class);
        });
    }

    private static @NotNull String resolveChartLocation(
            final @NotNull String chartName,
            final boolean withLocalCharts) {
        if (withLocalCharts) {
            return "/charts/" + chartName;
        }
        return "hivemq/" + chartName;
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static @NotNull String getRegistriesContent() {
        final var ociRegistry = URI.create("http://%s".formatted(K3S_DOCKER_IMAGE.getRegistry()));
        final int port = ociRegistry.getPort();
        if (port == -1) {
            throw new IllegalArgumentException("Registry '%s' does not specify a port".formatted(ociRegistry));
        }
        final var registry = "host.docker.internal:%d".formatted(port);
        LOG.debug("Setting up http://{} as OCI registry into K3s", registry);
        return """
                mirrors:
                  "host.docker.internal":
                    endpoint:
                      - "http://%s"
                """.formatted(registry);
    }

    private static @NotNull Stream<String> getOperatorFixedValues(final boolean withLocalCharts) {
        if (withLocalCharts) {
            return Stream.concat(getLocalOperatorRepositoryValues(), getOperatorFixedValues());
        }
        return getOperatorFixedValues();
    }

    private static @NotNull Stream<String> getPlatformFixedValues(final boolean withLocalCharts) {
        if (withLocalCharts) {
            return Stream.concat(getLocalPlatformRepositoryValues(), getPlatformFixedValues());
        }
        return getPlatformFixedValues();
    }

    private static @NotNull Stream<String> getLocalOperatorRepositoryValues() {
        // fixed values, loaded locally
        return Stream.of("--set", "image.repository=host.docker.internal/hivemq", "--set", "image.tag=snapshot");
    }

    private static @NotNull Stream<String> getLocalPlatformRepositoryValues() {
        // fixed values, loaded locally
        return Stream.of("--set", "image.repository=host.docker.internal/hivemq", "--set", "image.tag=latest");
    }

    private static @NotNull Stream<String> getOperatorFixedValues() {
        return Stream.of("--set", "logLevel=DEBUG",
                // need to limit cpu resource value for CI jobs
                "--set", "resources.cpu=" + POD_CPU_LIMIT);
    }

    private static @NotNull Stream<String> getPlatformFixedValues() {
        return Stream.of(
                // need to limit cpu resource value for CI jobs
                "--set", "nodes.resources.cpu=" + POD_CPU_LIMIT);
    }

    private class EventWatcher implements Watcher<Event> {

        private final @NotNull KubernetesClient client;

        EventWatcher(final @NotNull KubernetesClient client) {
            this.client = client;
        }

        @Override
        @SuppressWarnings("resource")
        public void eventReceived(final @NotNull Action action, final @NotNull Event event) {
            if (action != Action.ADDED && action != Action.MODIFIED) {
                return;
            }
            final var reason = event.getReason();
            final var note = event.getNote();
            if (reason == null || note == null) {
                return;
            }
            final var eventRegarding = event.getRegarding();
            if (eventRegarding == null || "kube-system".equals(eventRegarding.getNamespace())) {
                return;
            }

            final var namespace = eventRegarding.getNamespace();
            final var podName = eventRegarding.getName();
            final var podUid = eventRegarding.getUid();
            final var eventLog = "%s [%s] %s [%s:%s]".formatted(event.getType(), reason, note, namespace, podName);
            LOG.info("[{}] {}", LOG_PREFIX_EVENT, eventLog);
            logWaiter.accept(LOG_PREFIX_EVENT, eventLog);

            // extract and check container name
            if (!eventRegarding.getKind().equals("Pod")) {
                return;
            }
            final var fieldPath = eventRegarding.getFieldPath();
            if (fieldPath == null) {
                return;
            }
            final var containerName = LOG_WATCHER_CONTAINERS.stream()
                    .filter(c -> fieldPath.contains("{" + c + "}"))
                    .findFirst()
                    .orElse(null);
            if (containerName == null) {
                return;
            }
            LOG.info("Received {} event for container {} in pod {} [{}]", reason, containerName, podName, podUid);
            try {
                if (reason.equals("Created")) {
                    logWatches.computeIfAbsent(podUid + "-" + podName + "-" + containerName, key -> {
                        // create log watcher for container
                        final var logWatch = client.pods()
                                .inNamespace(namespace)
                                .withName(podName)
                                .inContainer(containerName)
                                .withReadyWaitTimeout((int) TimeUnit.SECONDS.toMillis(10))
                                .watchLog();
                        logWatch.onClose().thenRun(() -> {
                            logWatches.remove(podUid + "-" + podName + "-" + containerName);
                            LOG.info("Stopped log watcher for {} in pod {} [{}]", containerName, podName, podUid);
                        });
                        final var logPodName = getLogPodName(podName);
                        executorService.submit(() -> {
                            LOG.info("Started log watcher for {} in pod {} [{}]", containerName, podName, podUid);
                            try (final var inputStream = logWatch.getOutput();
                                 final var reader = new BufferedReader(new InputStreamReader(inputStream))) {
                                reader.lines().forEach(line -> {
                                    // skip the ISO8601 prefix for logging
                                    final var matcher = LOGBACK_DATE_PREFIX.matcher(line);
                                    final var printLine = matcher.matches() ? matcher.group(1) : line;
                                    // filter noisy platform logs
                                    if (printLine.startsWith("DEBUG - Flushing:")) {
                                        return;
                                    }
                                    LOG.info("[{}] [{}] [{}] {}",
                                            logPodName,
                                            containerName,
                                            podUid.substring(0, 8),
                                            printLine);
                                    logWaiter.accept(podName, line);
                                });
                            } catch (final Exception exception) {
                                LOG.error("Error while reading and processing log output for [{}] [{}]",
                                        logPodName,
                                        containerName,
                                        exception);
                            }
                        });
                        return logWatch;
                    });
                }
            } catch (final Exception e) {
                LOG.info("Uncaught exception in log watcher for {} in pod {} [{}]", containerName, podName, podUid, e);
            }
        }

        private @NotNull String getLogPodName(final @NotNull String podName) {
            final var maxLength = 20;
            if (podName.length() < maxLength) {
                return podName;
            }
            final var dashPos = podName.indexOf("-", maxLength);
            if (dashPos == -1) {
                return podName.substring(0, maxLength);
            }
            return podName.substring(0, dashPos);
        }

        @Override
        public void onClose(final @NotNull WatcherException cause) {
        }
    }

    private class PodWatcher implements Watcher<Pod> {

        @Override
        public void eventReceived(final @NotNull Action action, final @NotNull Pod pod) {
            final var container = pod.getSpec().getContainers().getFirst();
            if (container != null) {
                final var namespace = pod.getMetadata().getNamespace();
                final var podName = pod.getMetadata().getName();
                final var podUid = pod.getMetadata().getUid();
                final var podLog = "%s [%s] in %s was %s".formatted(podName, podUid, namespace, action);
                LOG.info("[{}] {}", LOG_PREFIX_POD, podLog);
                logWaiter.accept(LOG_PREFIX_POD, podLog);
            }
        }

        @Override
        public void onClose(final @NotNull WatcherException cause) {
        }
    }

    private static class K3sReadyStartupCheckStrategy extends StartupCheckStrategy {

        private final @NotNull HelmChartContainer container;

        K3sReadyStartupCheckStrategy(final @NotNull HelmChartContainer container) {
            this.container = container;
            this.withTimeout(Duration.ofSeconds(120));
        }

        @Override
        public @NotNull StartupStatus checkStartupState(
                final @NotNull DockerClient dockerClient,
                final @NotNull String containerId) {
            try {
                await().until(() -> container.getLogs(STDERR).matches("(?s).*Node controller sync successful.*"));
                // we need this to have the yaml read from the container
                container.containerIsStarted(container.getContainerInfo());
                final var yaml = container.getKubeConfigYaml();
                assertThat(yaml).isNotNull();
            } catch (final Exception e) {
                LOG.warn("K3s image not ready yet '{}'", e.getMessage());
                throw new RuntimeException(e);
            }
            LOG.debug("Successful Helm chart Container startup");
            return StartupStatus.SUCCESSFUL;
        }
    }
}
