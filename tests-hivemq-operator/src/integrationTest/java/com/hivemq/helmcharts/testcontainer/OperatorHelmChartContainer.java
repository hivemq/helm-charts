package com.hivemq.helmcharts.testcontainer;

import com.github.dockerjava.api.DockerClient;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.Pod;
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
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDERR;

/**
 * Container that includes the helm binary to be able to install the HiveMQ helm charts
 */
public class OperatorHelmChartContainer extends K3sContainer {
    public final static int MQTT_PORT = 1883;

    private static final @NotNull String LOG_PREFIX_EVENT = "EVENT";
    private static final @NotNull String LOG_PREFIX_POD = "POD";
    private static final @NotNull String LOG_PREFIX_K3S = "K3S";
    private static final @NotNull Pattern LOGBACK_DATE_PREFIX =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3} (.*)");
    private static final @NotNull String HIVEMQ_OPERATOR_CONTAINER_NAME = "operator";
    private static final @NotNull String HIVEMQ_CONTAINER_NAME = "hivemq";

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(OperatorHelmChartContainer.class);

    private final @NotNull ExecutorService executorService = Executors.newCachedThreadPool();
    private final @NotNull Map<String, Watch> watches = new ConcurrentHashMap<>();
    private final @NotNull Map<String, LogWatch> logWatches = new ConcurrentHashMap<>();
    private final @NotNull CountDownLatch terminateLatch = new CountDownLatch(1);
    private final @NotNull LogWaiterUtil logWaiter = new LogWaiterUtil();
    private final @NotNull List<String> imagesNames = new ArrayList<>();

    private final @NotNull String chartName;

    private @Nullable KubernetesClient kubernetesClient;
    private boolean withCustomImages = false;

    public OperatorHelmChartContainer(
            final @NotNull DockerImageNames.K3s k3s,
            final @NotNull String customValuesFile,
            final @NotNull String chartName) {
        super(getAdHocImageName(k3s));
        super.addExposedPort(MQTT_PORT);
        // mount all values for updates of the chart
        super.withClasspathResourceMapping("values", "/values/", BindMode.READ_ONLY);
        super.withCopyFileToContainer(MountableFile.forHostPath("../charts/hivemq-operator"), "/chart");
        super.withCopyFileToContainer(MountableFile.forClasspathResource(customValuesFile), "/files/values.yml");
        super.withStartupCheckStrategy(new DeploymentStatusStartupCheckStrategy(this));
        super.withLogConsumer(new K3sLogConsumer(LOG).withPrefix(LOG_PREFIX_K3S));
        super.withLogConsumer(outputFrame -> logWaiter.accept(LOG_PREFIX_K3S, outputFrame.getUtf8String().trim()));
        if (k3s.ordinal() > DockerImageNames.K3s.V1_24.ordinal()) {
            super.withCommand("server", "--disable=traefik", "--tls-san=" + getHost());
        }
        this.chartName = chartName;
    }

    private static @NotNull DockerImageName getAdHocImageName(final @NotNull DockerImageNames.K3s k3s) {
        final var dockerfile = Path.of(MountableFile.forClasspathResource("k3s.dockerfile").getFilesystemPath());
        // fix pre-emptively checking local images by replacing the build args in the Dockerfile
        // see https://github.com/testcontainers/testcontainers-java/issues/3238
        try {
            final var dockerfileString = Files.readString(dockerfile, UTF_8);
            Files.writeString(dockerfile, dockerfileString.replace("${K3S_VERSION}", k3s.getVersion()), UTF_8);
        } catch (IOException e) {
            LOG.warn("Could not replace build args in Dockerfile", e);
        }
        final var imageName = new ImageFromDockerfile().withDockerfile(dockerfile)
                .withBuildArg("K3S_VERSION", k3s.getVersion())
                .get();
        return DockerImageName.parse(imageName).asCompatibleSubstituteFor("rancher/k3s");
    }

    /**
     * Uses custom images instead of docker hub images, additional images can be appended
     */
    public @NotNull OperatorHelmChartContainer withLocalImages(final @Nullable String... fileNames) {
        withCustomImages = true;
        imagesNames.addAll(Arrays.asList("hivemq-dns-init-wait.tar", "hivemq-k8s.tar", "hivemq-operator.tar"));
        imagesNames.addAll(Arrays.asList(fileNames));
        super.withFileSystemBind("./build", "/build", BindMode.READ_ONLY);
        return this;
    }

    protected @NotNull List<String> getImagesNames() {
        return imagesNames;
    }

    public @NotNull KubernetesClient getKubernetesClient() {
        final var configYaml = this.getKubeConfigYaml();
        assertThat(configYaml).isNotNull();
        if (kubernetesClient == null) {
            kubernetesClient = new KubernetesClientBuilder().withConfig(Config.fromKubeconfig(configYaml)).build();
        }
        return kubernetesClient;
    }

    private void upgradeLocalChart(final @NotNull String chartName) throws Exception {
        this.upgradeLocalChart(chartName, "/files/values.yml");
    }

    public void upgradeLocalChart(final @NotNull String chartName, final @NotNull String valuesFilePath)
            throws Exception {
        // helm dependency update /chart
        final var outUpdate = this.execInContainer("/bin/helm", "dependency", "update", "/chart/");
        assertThat(outUpdate.getStderr()).isEmpty();

        // helm --kubeconfig /etc/rancher/k3s/k3s.yaml install hivemq /chart -f /files/values.yml
        final var execDeploy = this.execInContainer("/bin/helm",
                "--kubeconfig",
                "/etc/rancher/k3s/k3s.yaml",
                "upgrade",
                "--install",
                chartName,
                "/chart",
                "-f",
                valuesFilePath);

        if (!execDeploy.getStderr().isEmpty()) {
            // shows also warnings
            LOG.warn(execDeploy.getStderr());
        }
        assertThat(execDeploy.getStdout()).describedAs(execDeploy::getStderr).isNotEmpty();
        LOG.debug("Chart '{}' installed or upgraded", chartName);
    }

    @Override
    public final void start() {
        super.start();
        final var client = getKubernetesClient();
        watches.put("events", client.events().v1().events().inAnyNamespace().watch(new EventWatcher(client)));
        watches.put("pods", client.pods().inAnyNamespace().watch(new PodWatcher()));
        this.kubernetesClient = client;
    }

    @Override
    public final void stop() {
        terminateLatch.countDown();
        logWatches.values().forEach(LogWatch::close);
        watches.values().forEach(Watch::close);
        final var client = kubernetesClient;
        if (client != null) {
            client.close();
        }
        executorService.shutdownNow();
        super.stop();
    }

    private class DeploymentStatusStartupCheckStrategy extends StartupCheckStrategy {
        private final @NotNull OperatorHelmChartContainer container;

        public DeploymentStatusStartupCheckStrategy(@NotNull final OperatorHelmChartContainer container) {
            this.container = container;
            this.withTimeout(Duration.ofSeconds(240));
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
                if (container.withCustomImages) {
                    loadImages();
                }
                final var client = container.getKubernetesClient();

                upgradeLocalChart(chartName);
                K8sUtil.waitForHiveMQClusterState(client, "default", chartName, "Running");

                // get the HiveMQ container logs inside the pod
                final var pod = client.pods().inAnyNamespace().withLabel("app", "hivemq").list().getItems().getFirst();
                final var containerResource = client.pods()
                        .inNamespace("default")
                        .withName(pod.getMetadata().getName())
                        .inContainer("hivemq");
                assertThat(containerResource.getLog()).doesNotContain(
                        "Could not read the configuration file /opt/hivemq/conf/config.xml. Using default config");
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
            return StartupStatus.SUCCESSFUL;
        }

        public void loadImages() {
            container.getImagesNames().forEach(a -> {
                try {
                    final var outLoadImage = container.execInContainer("/bin/ctr", "images", "import", "/build/" + a);
                    assertThat(outLoadImage.getStdout()).isNotEmpty();
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
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
                            try (final var inputStream = logWatch.getOutput();
                                 final var reader = new BufferedReader(new InputStreamReader(inputStream))) {
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
                            } catch (final Exception ignored) {
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
            if (note.endsWith("container " + HIVEMQ_CONTAINER_NAME)) {
                return HIVEMQ_CONTAINER_NAME;
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
            final var container = pod.getSpec().getContainers().getFirst();
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
}
