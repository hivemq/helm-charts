package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;

/**
 * Monitoring utility class for the HiveMQ Platform metrics.
 */
public class MonitoringUtil {

    private static final int DEFAULT_METRICS_COUNT = 30;

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(MonitoringUtil.class);

    private MonitoringUtil() {
    }

    /**
     * Asserts the monitoring probes for both subscribe and publishes metrics.
     *
     * @param client             the Kubernetes client to create the resources with
     * @param namespace          the namespace to use for metadata
     * @param metricsServiceName the metrics service name to fetch the probes from
     * @param metricsServicePort the metrics service port to fetch the probes from
     */
    public static void assertSubscribesPublishesMetrics(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String metricsServiceName,
            final int metricsServicePort) {
        assertSubscribesPublishesMetrics(client,
                namespace,
                metricsServiceName,
                metricsServicePort,
                "/",
                DEFAULT_METRICS_COUNT);
    }

    /**
     * Asserts the monitoring probes for both subscribe and publishes metrics.
     *
     * @param client             the Kubernetes client to create the resources with
     * @param namespace          the namespace to use for metadata
     * @param metricsServiceName the metrics service name to fetch the probes from
     * @param metricsServicePort the metrics service port to fetch the probes from
     * @param metricsPath        the metrics service path for the monitoring URL endpoint
     */
    public static void assertSubscribesPublishesMetrics(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String metricsServiceName,
            final int metricsServicePort,
            final @NotNull String metricsPath) {
        assertSubscribesPublishesMetrics(client,
                namespace,
                metricsServiceName,
                metricsServicePort,
                metricsPath,
                DEFAULT_METRICS_COUNT);
    }

    /**
     * Asserts the monitoring probes for publishes metrics.
     *
     * @param client             the Kubernetes client to create the resources with
     * @param namespace          the namespace to use for metadata
     * @param metricsServiceName the metrics service name to fetch the probes from
     * @param metricsServicePort the metrics service port to fetch the probes from
     */
    public static void assertPublishesMetrics(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String metricsServiceName,
            final int metricsServicePort) {
        assertPublishesMetrics(client, namespace, metricsServiceName, metricsServicePort, "/", DEFAULT_METRICS_COUNT);
    }

    /**
     * Asserts the monitoring probes for both subscribes and publishes metrics.
     *
     * @param client               the Kubernetes client to create the resources with
     * @param namespace            the namespace to use for metadata
     * @param metricsServiceName   the metrics service name to fetch the probes from
     * @param metricsServicePort   the metrics service port to fetch the probes from
     * @param metricsPath          the metrics service path for the monitoring URL endpoint
     * @param expectedMetricsCount the expected metrics count to assert with
     */
    public static void assertSubscribesPublishesMetrics(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String metricsServiceName,
            final int metricsServicePort,
            final @NotNull String metricsPath,
            final long expectedMetricsCount) {
        assertSubscribesMetrics(client,
                namespace,
                metricsServiceName,
                metricsServicePort,
                metricsPath,
                expectedMetricsCount);
        assertPublishesMetrics(client,
                namespace,
                metricsServiceName,
                metricsServicePort,
                metricsPath,
                expectedMetricsCount);
    }

    /**
     * Asserts the monitoring probes for subscribes metrics.
     *
     * @param client             the Kubernetes client to create the resources with
     * @param namespace          the namespace to use for metadata
     * @param metricsServiceName the metrics service name to fetch the probes from
     * @param metricsServicePort the metrics service port to fetch the probes from
     */
    public static void assertSubscribesMetrics(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String metricsServiceName,
            final int metricsServicePort) {
        assertSubscribesMetrics(client, namespace, metricsServiceName, metricsServicePort, "/", DEFAULT_METRICS_COUNT);
    }

    /**
     * Asserts the monitoring probes for subscribes metrics.
     *
     * @param client               the Kubernetes client to create the resources with
     * @param namespace            the namespace to use for metadata
     * @param metricsServiceName   the metrics service name to fetch the probes from
     * @param metricsServicePort   the metrics service port to fetch the probes from
     * @param metricsPath          the metrics service path for the monitoring URL endpoint
     * @param expectedMetricsCount the expected metrics count to assert with
     */
    public static void assertSubscribesMetrics(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String metricsServiceName,
            final int metricsServicePort,
            final @NotNull String metricsPath,
            final long expectedMetricsCount) {
        LOG.info("Asserting Prometheus subscribes metrics...");
        final var started = System.nanoTime();
        try (final var portForward = K8sUtil.getPortForward(client,
                namespace,
                metricsServiceName,
                metricsServicePort)) {
            await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
                final var prometheusMetrics = PrometheusUtil.getPrometheusMetrics(portForward, metricsPath);

                // assert the total rate of incoming MQTT SUBSCRIBE messages
                assertThat(prometheusMetrics.get("com_hivemq_messages_incoming_subscribe_rate_total")) //
                        .as("HiveMQ Platform total rate of incoming MQTT SUBSCRIBE messages\n%s", prometheusMetrics)
                        .isEqualTo(expectedMetricsCount);

                // assert the total count of every incoming MQTT SUBSCRIBE message
                assertThat(prometheusMetrics.get("com_hivemq_messages_incoming_subscribe_count")) //
                        .as("HiveMQ Platform total count of incoming MQTT SUBSCRIBE messages\n%s", prometheusMetrics)
                        .isEqualTo(expectedMetricsCount);

                // assert the total rate of outgoing MQTT PUBLISH messages
                assertThat(prometheusMetrics.get("com_hivemq_messages_outgoing_publish_rate_total")) //
                        .as("HiveMQ Platform total rate of outgoing MQTT PUBLISH messages\n%s", prometheusMetrics)
                        .isEqualTo(expectedMetricsCount);

                // assert the total count of every outgoing MQTT PUBLISH message
                assertThat(prometheusMetrics.get("com_hivemq_messages_outgoing_publish_count")) //
                        .as("HiveMQ Platform total count of outgoing MQTT PUBLISH messages\n%s", prometheusMetrics)
                        .isEqualTo(expectedMetricsCount);

                // assert the total rate of dropped messages
                assertThat(prometheusMetrics.get("com_hivemq_messages_dropped_rate_total")) //
                        .as("HiveMQ Platform total rate of dropped messages\n%s", prometheusMetrics).isEqualTo(0.0f);

            });
            LOG.info("Asserted subscribes metrics ({} ms)", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (final IOException e) {
            throw new AssertionError("Could not assert Prometheus subscribes metrics", e);
        }
    }

    /**
     * Asserts the monitoring probes for publishes metrics.
     *
     * @param client               the Kubernetes client to create the resources with
     * @param namespace            the namespace to use for metadata
     * @param metricsServiceName   the metrics service name to fetch the probes from
     * @param metricsServicePort   the metrics service port to fetch the probes from
     * @param metricsPath          the metrics service path for the monitoring URL endpoint
     * @param expectedMetricsCount the expected metrics count to assert with
     */
    public static void assertPublishesMetrics(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String metricsServiceName,
            final int metricsServicePort,
            final @NotNull String metricsPath,
            final long expectedMetricsCount) {
        LOG.info("Asserting Prometheus publishes metrics...");
        final var started = System.nanoTime();
        try (final var portForward = K8sUtil.getPortForward(client,
                namespace,
                metricsServiceName,
                metricsServicePort)) {
            await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() -> {
                final var prometheusMetrics = PrometheusUtil.getPrometheusMetrics(portForward, metricsPath);

                // assert the total rate of incoming MQTT PUBLISH messages
                assertThat(prometheusMetrics.get("com_hivemq_messages_incoming_publish_rate_total")) //
                        .as("HiveMQ Platform total rate of incoming MQTT PUBLISH messages\n%s", prometheusMetrics)
                        .isEqualTo(expectedMetricsCount);

                // assert the total count of every incoming MQTT PUBLISH message
                assertThat(prometheusMetrics.get("com_hivemq_messages_incoming_publish_count")) //
                        .as("HiveMQ Platform total count of incoming MQTT PUBLISH messages\n%s", prometheusMetrics)
                        .isEqualTo(expectedMetricsCount);

                // assert the total rate of dropped messages
                assertThat(prometheusMetrics.get("com_hivemq_messages_dropped_rate_total")) //
                        .as("HiveMQ Platform total rate of dropped messages\n%s", prometheusMetrics).isEqualTo(0.0f);

            });
            LOG.info("Asserted publishes metrics ({} ms)", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (final IOException e) {
            throw new AssertionError("Could not assert Prometheus publishes metrics", e);
        }
    }
}
