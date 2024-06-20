package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

public class K8sUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(K8sUtil.class);

    private K8sUtil() {
    }

    public static @NotNull Resource<GenericKubernetesResource> getHiveMQCluster(
            final @NotNull KubernetesClient client, final @NotNull String namespace, final @NotNull String name) {
        final var context = new ResourceDefinitionContext.Builder().withGroup("hivemq.com")
                .withKind("HiveMQCluster")
                .withVersion("v1")
                .withPlural("hivemq-clusters")
                .withNamespaced(true)
                .build();
        return client.genericKubernetesResources(context).inNamespace(namespace).withName(name);
    }

    /**
     * Returns a Predicate condition based on the state of a Kubernetes resource.
     */
    @SuppressWarnings("unchecked")
    public static @NotNull Predicate<GenericKubernetesResource> getHiveMQClusterStatus(final @NotNull String state) {
        return resource -> {
            if (resource == null) {
                return false;
            }
            final var status = (Map<String, String>) resource.getAdditionalProperties().get("status");
            if (status == null) {
                return false;
            }
            LOG.debug("State to compare {} and waiting for {}", status.get("state"), state);
            return status.get("state").contains(state);
        };
    }

    /**
     * Waits for the given custom resource to be in the desired state.
     */
    public static void waitForHiveMQClusterState(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName,
            final @NotNull String state) {
        final var hivemqCustomResource = K8sUtil.getHiveMQCluster(client, namespace, customResourceName);
        hivemqCustomResource.waitUntilCondition(getHiveMQClusterStatus(state), 5, TimeUnit.MINUTES);
        assertThat(hivemqCustomResource.get().get("status").toString()).contains(state);
    }
}
