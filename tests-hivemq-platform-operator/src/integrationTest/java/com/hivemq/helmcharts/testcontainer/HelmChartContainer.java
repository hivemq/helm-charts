package com.hivemq.helmcharts.testcontainer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.dockerjava.api.DockerClient;
import com.hivemq.helmcharts.Chart;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;

public class HelmChartContainer extends K3sContainer {

    public static final @NotNull String MANIFEST_FILES = "manifests";

    private static final @NotNull String DOCKER_SECRET_ENV_NOT_FOUND = "DOCKER_SECRET_ENV_NOT_FOUND";
    private static final @NotNull String OPERATOR_CHART = "hivemq-platform-operator";
    private static final @NotNull String PLATFORM_CHART = "hivemq-platform";
    private static final @NotNull String OPERATOR_IMAGE_NAME = "hivemq-platform-operator-test";
    private static final @NotNull String OPERATOR_INIT_IMAGE_NAME = "hivemq-platform-operator-init-test";
    private static final @NotNull String PLATFORM_IMAGE_TAG = System.getProperty("hivemq.version");
    private static final @NotNull String LOG_PREFIX_EVENT = "EVENT";
    private static final @NotNull String LOG_PREFIX_POD = "POD";
    private static final @NotNull String LOG_PREFIX_K3S = "K3S";
    private static final @NotNull Pattern LOGBACK_DATE_PREFIX =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3} (.*)");
    private static final @NotNull String HIVEMQ_OPERATOR_CONTAINER_NAME = "hivemq-platform-operator";
    private static final @NotNull String HIVEMQ_PLATFORM_CONTAINER_NAME = "hivemq";
    private static final @NotNull String POD_CPU_LIMIT = "512m";

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmChartContainer.class);

    private final @NotNull ExecutorService executorService = Executors.newCachedThreadPool();
    private final @NotNull Map<String, String> imageNamePaths = new ConcurrentHashMap<>();
    private final @NotNull Map<String, Watch> watches = new ConcurrentHashMap<>();
    private final @NotNull Map<String, LogWatch> logWatches = new ConcurrentHashMap<>();
    private final @NotNull CountDownLatch terminateLatch = new CountDownLatch(1);
    private final @NotNull LogWaiterUtil logWaiter = new LogWaiterUtil();
    private @Nullable Chart currentPlatformChart;

    private @Nullable KubernetesClient client;
    private boolean withLocalImages = true;
    private boolean withK3sDebugging;

    public HelmChartContainer() {
        this(DockerImageNames.K3s.LATEST);
    }

    public HelmChartContainer(final @NotNull DockerImageNames.K3s k3s) {
        super(getAdHocImageName(k3s));
        super.withClasspathResourceMapping("values", "/files/", BindMode.READ_ONLY);
        super.withCopyFileToContainer(MountableFile.forHostPath("../charts/" + OPERATOR_CHART), "/charts/" + OPERATOR_CHART);
        super.withCopyFileToContainer(MountableFile.forHostPath("../charts/" + PLATFORM_CHART), "/charts/" + PLATFORM_CHART);
        super.withCopyFileToContainer(MountableFile.forHostPath("../" + MANIFEST_FILES), "/" + MANIFEST_FILES);
        super.withCopyFileToContainer(MountableFile.forHostPath("../scripts/test.sh"), "/bin/test.sh");

        super.withStartupCheckStrategy(new K3sReadyStartupCheckStrategy(this));
        super.withLogConsumer(new K3sLogConsumer(LOG).withPrefix(LOG_PREFIX_K3S));
        super.withLogConsumer(outputFrame -> logWaiter.accept(LOG_PREFIX_K3S, outputFrame.getUtf8String().trim()));
        if (withLocalImages) {
            bindLocalImages();
        }
        if (k3s.ordinal() > DockerImageNames.K3s.V1_24.ordinal()) {
            if (withK3sDebugging) {
                super.withCommand("server", "--disable=traefik", "--tls-san=" + getHost(), "--debug", "-v", "10");
            } else {
                super.withCommand("server", "--disable=traefik", "--tls-san=" + getHost());
            }
        } else if (withK3sDebugging) {
            super.withCommand("server", "--no-deploy=traefik", "--tls-san=" + getHost(), "--debug", "-v", "10");
        }
    }

    /**
     * Checks if there are any images on the build containers directory to be loaded into K3s locally.
     * Otherwise, it will try to pull the images from the Github container registry by using the secret set as
     * an environment variables.
     * See { @link #createContainerRegistrySecret() createContainerRegistrySecret} method
     */
    private void bindLocalImages() {
        try {
            final Path buildPath = Path.of("build/");
            try (var files = Files.list(buildPath).filter(file -> file.toString().endsWith(".tar"))) {
                files.map(file -> file.getFileName().toString()).forEach(s -> imageNamePaths.put(s, "/containers"));
            }
            if (!imageNamePaths.isEmpty()) {
                super.withFileSystemBind("build/", "/containers", BindMode.READ_ONLY);
            } else {
                LOG.warn("No container image files could be found on local path {}", buildPath.toAbsolutePath());
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public @NotNull Chart getCurrentPlatformChart() {
        final var platformChart = currentPlatformChart;
        if (platformChart != null) {
            return platformChart;
        }
        final var objectMapper = new ObjectMapper(new YAMLFactory());
        currentPlatformChart = copyFileFromContainer("/charts/" + PLATFORM_CHART + "/Chart.yaml",
                inputStream -> objectMapper.readValue(inputStream, Chart.class));
        return currentPlatformChart;
    }

    public @NotNull Chart getPreviousPlatformChart() throws Exception {
        final var currentChart = getCurrentPlatformChart();
        final var regex = String.format("*.%s*", currentChart.getDescription());
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
        super.start();
        addHiveMQHelmRepo();
        final var client = getKubernetesClient();
        watches.put("events", client.events().v1().events().inAnyNamespace().watch(new EventWatcher(client)));
        watches.put("pods", client.pods().inAnyNamespace().watch(new PodWatcher()));
        this.client = client;
    }

    @Override
    public final void stop() {
        terminateLatch.countDown();
        logWatches.values().forEach(LogWatch::close);
        watches.values().forEach(Watch::close);
        if (client != null) {
            client.close();
        }
        executorService.shutdownNow();
        super.stop();
    }

    @SuppressWarnings("unused")
    public @NotNull HelmChartContainer withK3sDebugging() {
        withK3sDebugging = true;
        return this;
    }

    public @NotNull HelmChartContainer withNetwork(final @NotNull Network network) {
        super.withNetwork(network);
        return this;
    }

    @SuppressWarnings("unused")
    public @NotNull HelmChartContainer withLocalImages(final boolean withLocalImages) {
        this.withLocalImages = withLocalImages;
        return this;
    }

    public void createNamespace(final @NotNull String name) {
        final var namespace = new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
        final var response = getKubernetesClient().namespaces().resource(namespace).create();
        assertThat(response).isNotNull();
        createContainerRegistrySecret(name, this);
    }

    public void deleteNamespace(final @NotNull String name) {
        final var namespace = new NamespaceBuilder().withNewMetadata().withName(name).endMetadata().build();
        final var response = getKubernetesClient().namespaces().resource(namespace).delete();
        assertThat(response).isNotNull();
    }

    public @NotNull LogWaiterUtil getLogWaiter() {
        return logWaiter;
    }

    public @NotNull KubernetesClient getKubernetesClient() {
        if (client == null) {
            final var config = Config.fromKubeconfig(getKubeConfigYaml());
            return new KubernetesClientBuilder().withConfig(config).build();
        }
        return client;
    }

    public void installOperatorChart(
            final @NotNull String releaseName,
            final boolean withLocalCharts,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("install",
                releaseName,
                resolveChartLocation(OPERATOR_CHART, withLocalCharts),
                withLocalCharts,
                Stream.concat(getOperatorFixedValues(withLocalCharts), Stream.of(additionalCommands)),
                true);
    }

    public void installOperatorChart(final @NotNull String releaseName, final @NotNull String... additionalCommands)
            throws Exception {
        installOperatorChart(releaseName, true, additionalCommands);
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

    public void uninstallRelease(final @NotNull String releaseName, final @NotNull String... additionalCommands)
            throws Exception {
        executeHelmCommand("uninstall", releaseName, null, true, Stream.of(additionalCommands), false);
    }

    public void upgradeOperatorChart(
            final @NotNull String releaseName,
            final boolean withLocalCharts,
            final @NotNull String... additionalCommands) throws Exception {
        executeHelmCommand("upgrade",
                releaseName,
                resolveChartLocation(OPERATOR_CHART, withLocalCharts),
                withLocalCharts,
                Stream.concat(getOperatorFixedValues(withLocalCharts), Stream.of(additionalCommands)),
                true);
    }

    public void upgradeOperatorChart(final @NotNull String releaseName, final @NotNull String... additionalCommands)
            throws Exception {
        upgradeOperatorChart(releaseName, true, additionalCommands);
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

    private void addHiveMQHelmRepo() {
        // helm --kubeconfig /etc/rancher/k3s/k3s.yaml repo add hivemq https://hivemq.github.io/helm-charts
        final var helmCommandList = new ArrayList<>(List.of("/bin/helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                "repo",
                "add",
                "hivemq",
                "https://hivemq.github.io/helm-charts"));
        LOG.debug("Executing helm command: {}", String.join(" ", helmCommandList));
        try {
            final var execResult = execInContainer(helmCommandList.toArray(new String[0]));
            assertThat(execResult.getStderr()).as("stdout: %s\nstderr: %s", execResult.getStdout(), execResult.getStderr())
                    .isEmpty();
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    private @NotNull String executeHelmCommand(
            final @NotNull String helmCommand,
            final @NotNull String releaseName,
            final @Nullable String chartName,
            final boolean withLocalCharts,
            final @NotNull Stream<String> additionalCommands,
            final boolean debugOnFailure) throws Exception {
        // helm --kubeconfig /etc/rancher/k3s/k3s.yaml <install|upgrade> test-operator /chart/hivemq-platform-operator --wait --timeout 5m0s
        final var helmCommandList = new ArrayList<>(List.of("/bin/helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                helmCommand,
                releaseName,
                chartName != null ? chartName : "",
                "--wait",
                "--timeout",
                "5m0s"));
        final var additionalCommandsList = additionalCommands.collect(Collectors.toList());
        helmCommandList.addAll(additionalCommandsList);
        if (chartName != null && withLocalCharts) {
            // helm dependency update /chart
            final var outUpdate = execInContainer("/bin/helm", "dependency", "update", chartName);
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
            final @NotNull String chartName, final @NotNull Stream<String> additionalCommands) throws Exception {
        // helm --kubeconfig /etc/rancher/k3s/k3s.yaml search repo <repo|chart>
        final var helmCommandList = new ArrayList<>(List.of("/bin/helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                "search",
                "repo",
                chartName));
        final var additionalCommandsList = additionalCommands.collect(Collectors.toList());
        helmCommandList.addAll(additionalCommandsList);

        LOG.debug("Executing helm command: {}", String.join(" ", helmCommandList));
        final var execResult = execInContainer(helmCommandList.toArray(new String[0]));
        assertThat(execResult.getStderr()).as("stdout: %s\nstderr: %s", execResult.getStdout(), execResult.getStderr())
                .isEmpty();
        return execResult.getStdout();
    }

    private @NotNull String resolveChartLocation(final @NotNull String chartName, final boolean withLocalCharts) {
        if (withLocalCharts) {
            return "/charts/" + chartName;
        }
        return "hivemq/" + chartName;
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
            return String.format("Helm command: %s\nstdout: %s\nstderr: %s\nyaml:\n%s",
                    helmCommandList,
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

    private static @NotNull DockerImageName getAdHocImageName(final @NotNull DockerImageNames.K3s k3s) {
        final var dockerfile = new File(MountableFile.forClasspathResource("/helm.dockerfile").getFilesystemPath());
        final var imageName = new ImageFromDockerfile().withDockerfile(dockerfile.toPath())
                .withBuildArg("K3S_VERSION", k3s.getVersion())
                .get();
        return DockerImageName.parse(imageName).asCompatibleSubstituteFor("rancher/k3s");
    }

    private static void createContainerRegistrySecret(
            final @NotNull String namespace, final @NotNull K3sContainer container) {
        final var config = Config.fromKubeconfig(container.getKubeConfigYaml());
        final var dockerSecret = getDockerSecret();
        if (dockerSecret.equals(DOCKER_SECRET_ENV_NOT_FOUND)) {
            LOG.warn("Docker Secret not created, images have to be loaded into K3s");
            return;
        }
        final var secret = new SecretBuilder().withNewMetadata()
                .withName("ghcr")
                .endMetadata()
                .withData(Collections.singletonMap(".dockerconfigjson",
                        Base64.getEncoder().encodeToString(dockerSecret.getBytes(StandardCharsets.UTF_8))))
                .withType("kubernetes.io/dockerconfigjson")
                .build();
        try (final var client = new KubernetesClientBuilder().withConfig(config).build()) {
            final var createdSecret = client.resource(secret).inNamespace(namespace).create();
            assertThat(createdSecret).isNotNull();
            LOG.debug("Docker secret '{}' created in namespace '{}'",
                    createdSecret.getMetadata().getName(),
                    createdSecret.getMetadata().getNamespace());
        }
    }

    private static @NotNull String getDockerSecret() {
        final String username = System.getenv("GITHUB_USER_NAME");
        final String email = System.getenv("GITHUB_EMAIL");
        final String password = System.getenv("GITHUB_PAT");
        if (username == null || email == null || password == null) {
            LOG.warn("Missing credentials for ghcr.io check the containers are available in the image. " +
                    "Define username, email and PAT via env variables " +
                    "GITHUB_USER_NAME, GITHUB_EMAIL and GITHUB_PAT");
            return DOCKER_SECRET_ENV_NOT_FOUND;
        }
        return "{ \"auths\":{" +
                "\"https://ghcr.io\":{" +
                "\"username\":\"" +
                username +
                "\",\"email\":\"" +
                email +
                "\",\"password\":\"" +
                password +
                "\"" +
                "}}}";
    }

    private static @NotNull Stream<String> getOperatorFixedValues(final boolean withLocalCharts) {
        if (withLocalCharts) {
            return Stream.concat(getLocalOperatorRepositoryValues(), getOperatorResourcesFixedValues());
        }
        return getOperatorResourcesFixedValues();
    }

    private static @NotNull Stream<String> getPlatformFixedValues(final boolean withLocalCharts) {
        if (withLocalCharts) {
            return Stream.concat(getLocalPlatformRepositoryValues(), getPlatformResourcesFixedValues());
        }
        return getPlatformResourcesFixedValues();
    }

    private static @NotNull Stream<String> getLocalOperatorRepositoryValues() {
        // fixed values, loaded locally
        return Stream.of("--set",
                "image.repository=docker.io/hivemq",
                "--set",
                "image.name=" + OPERATOR_IMAGE_NAME,
                "--set",
                "image.initImageName=" + OPERATOR_INIT_IMAGE_NAME,
                "--set",
                "image.tag=snapshot",
                "--set",
                "image.pullPolicy=Never");
    }

    private static @NotNull Stream<String> getLocalPlatformRepositoryValues() {
        // fixed values, loaded locally
        return Stream.of("--set",
                "image.repository=docker.io/hivemq",
                "--set",
                "image.tag=" + PLATFORM_IMAGE_TAG,
                "--set",
                "image.pullPolicy=Never");
    }

    private static @NotNull Stream<String> getOperatorResourcesFixedValues() {
        // need to limit cpu resource value for CI jobs
        return Stream.of("--set", "resources.cpu=" + POD_CPU_LIMIT);
    }

    private static @NotNull Stream<String> getPlatformResourcesFixedValues() {
        // need to limit cpu resource value for CI jobs
        return Stream.of("--set", "nodes.resources.cpu=" + POD_CPU_LIMIT);
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
            final var eventLog = String.format("%s [%s] %s [%s:%s]", event.getType(), reason, note, namespace, podName);
            LOG.info("[{}] {}", LOG_PREFIX_EVENT, eventLog);
            logWaiter.accept(LOG_PREFIX_EVENT, eventLog);

            final var containerName = getContainerNameFromEventNote(note);
            if (containerName == null) {
                return;
            }
            try {
                if (reason.equals("Created")) {
                    logWatches.computeIfAbsent(podUid + "-" + podName + "-" + containerName, key -> {
                        // create log watcher for container
                        final var logWatch = client.pods()
                                .inNamespace(namespace)
                                .withName(podName)
                                .inContainer(containerName)
                                .watchLog();
                        final var logPodName = getLogPodName(podName);
                        executorService.submit(() -> {
                            LOG.info("Started log watcher for {} in pod {} [{}]", containerName, podName, podUid);
                            try (final InputStream inputStream = logWatch.getOutput();
                                 final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                                reader.lines().forEach(line -> {
                                    // skip the ISO8601 prefix for logging
                                    final var matcher = LOGBACK_DATE_PREFIX.matcher(line);
                                    final var printLine = matcher.matches() ? matcher.group(1) : line;
                                    LOG.info("[{}] [{}] [{}] {}",
                                            logPodName,
                                            containerName,
                                            podUid.substring(0, 8),
                                            printLine);
                                    logWaiter.accept(podName, line);
                                });
                            } catch (final Exception exception) {
                                LOG.error("Error while reading and processing log output for [{}] [{}]", logPodName, containerName, exception);
                            }
                            removeAndCloseLogWatcher(containerName, podName, podUid);
                        });
                        return logWatch;
                    });
                } else if (reason.equals("Killing")) {
                    // close log watcher for container
                    removeAndCloseLogWatcher(containerName, podName, podUid);
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

        private void removeAndCloseLogWatcher(
                final @NotNull String containerName, final @NotNull String podName, final @NotNull String podUid) {
            final var logWatch = logWatches.remove(podUid + "-" + podName + "-" + containerName);
            if (logWatch != null) {
                logWatch.close();
                LOG.info("Stopped log watcher for {} in pod {} [{}]", containerName, podName, podUid);
            }
        }

        @Override
        public void onClose(final @NotNull WatcherException cause) {
        }

        private @Nullable String getContainerNameFromEventNote(final @NotNull String note) {
            if (note.endsWith("container " + HIVEMQ_PLATFORM_CONTAINER_NAME)) {
                return HIVEMQ_PLATFORM_CONTAINER_NAME;
            }
            if (note.endsWith("container " + HIVEMQ_OPERATOR_CONTAINER_NAME)) {
                return HIVEMQ_OPERATOR_CONTAINER_NAME;
            }
            return null;
        }
    }

    private class PodWatcher implements Watcher<Pod> {

        @Override
        public void eventReceived(final @NotNull Action action, final @NotNull Pod pod) {
            final var container = pod.getSpec().getContainers().get(0);
            if (container != null) {
                final var namespace = pod.getMetadata().getNamespace();
                final var podName = pod.getMetadata().getName();
                final var podUid = pod.getMetadata().getUid();
                final var podLog = String.format("%s [%s] in %s was %s", podName, podUid, namespace, action);
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
                final @NotNull DockerClient dockerClient, final @NotNull String containerId) {
            try {
                await().until(() -> container.getLogs(STDERR).matches("(?s).*Node controller sync successful.*"));
                // we need this to have the yaml read from the container
                container.containerIsStarted(container.getContainerInfo());
                final var yaml = container.getKubeConfigYaml();
                assertThat(yaml).isNotNull();
                createContainerRegistrySecret("default", container);
                loadLocalImages();
            } catch (final Exception e) {
                LOG.warn("K3s image not ready yet '{}'", e.getMessage());
                throw new RuntimeException(e);
            }
            LOG.debug("Successful Helm chart Container startup");
            return StartupStatus.SUCCESSFUL;
        }

        private void loadLocalImages() {
            container.imageNamePaths.forEach((imageName, containerPath) -> {
                try {
                    final var execResult =
                            container.execInContainer("/bin/ctr", "images", "import", containerPath + "/" + imageName);
                    assertThat(execResult.getStderr()).as("Unable to load %s image - %s",
                            imageName,
                            execResult.getStderr()).isEmpty();
                    LOG.debug("Image file loaded '{}'", imageName);
                } catch (final Exception e) {
                    throw new RuntimeException("Failed loading container image " + imageName, e);
                }
            });
        }
    }
}
