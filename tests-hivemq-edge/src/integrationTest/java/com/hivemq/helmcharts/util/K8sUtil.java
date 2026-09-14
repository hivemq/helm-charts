package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class K8sUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(K8sUtil.class);

    private K8sUtil() {
    }

    /**
     * Returns the default labels defined for a HiveMQ Edge pod (matches the chart's selector-labels helper).
     *
     * @param releaseName the release name of the Edge chart
     * @return Map containing the selector labels expected for an Edge pod.
     */
    public static @NotNull Map<String, String> getHiveMQEdgeLabels(final @NotNull String releaseName) {
        return Map.of("app.kubernetes.io/instance", releaseName, "app.kubernetes.io/name", "hivemq-edge");
    }

    /**
     * @param clazz Class object to get the namespace from.
     * @return Namespace generated from the class passed as argument, up to 63 characters.
     */
    public static @NotNull String getNamespaceName(final @NotNull Class<?> clazz) {
        final var maxLength = 63;
        final var namespace = clazz.getSimpleName().toLowerCase();
        if (namespace.length() > maxLength) {
            return namespace.substring(0, maxLength);
        }
        return namespace;
    }

    /**
     * Returns the {@link LocalPortForward} object the service and target port passed as parameters.
     *
     * @param client      the Kubernetes client to use
     * @param namespace   the namespace to look up for the service to expose
     * @param serviceName the service name to port forward
     * @param targetPort  the service port to forward
     * @return {@link LocalPortForward}
     */
    public static @NotNull LocalPortForward getPortForward(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String serviceName,
            final int targetPort) {
        return client.services().inNamespace(namespace).withName(serviceName).portForward(targetPort);
    }

    /**
     * Waits for the HiveMQ Edge pod based on the given release name to be in a running status.
     */
    public static void waitForHiveMQEdgePodStateRunning(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String releaseName) {
        waitForPodStateRunning(client, namespace, getHiveMQEdgeLabels(releaseName));
    }

    /**
     * Waits for the pod based on the given labels to be in a running status.
     */
    public static void waitForPodStateRunning(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull Map<String, String> labels) {
        client.pods()
                .inNamespace(namespace)
                .withLabels(labels)
                .waitUntilCondition(pod -> pod != null &&
                        pod.getStatus().getContainerStatuses().stream().allMatch(containerStatus -> {
                            final var ready = containerStatus.getReady() && containerStatus.getStarted();
                            if (ready) {
                                LOG.info("Pod '{}' is ready", pod.getMetadata().getName());
                            } else {
                                LOG.debug("Waiting for Pod '{}' to be ready: {}",
                                        pod.getMetadata().getName(),
                                        containerStatus);
                            }
                            return ready;
                        }), 3, TimeUnit.MINUTES);
    }
}
