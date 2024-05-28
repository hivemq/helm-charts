package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class K8sUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(K8sUtil.class);

    private K8sUtil() {
    }

    /**
     * Creates a ConfigMap from the given resource file on the classpath.
     *
     * @param client       the Kubernetes client to use
     * @param namespace    the namespace to create the custom resource in
     * @param resourceName the name of the resource file to use
     * @return the created ConfigMap instance
     */
    public static @NotNull ConfigMap createConfigMap(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String resourceName) {
        return loadResource(client, namespace, resourceName, ConfigMap.class).create();
    }

    /**
     * Creates a {@link ConfigMap} with a {@code config.xml} entry with the given configuration.
     *
     * @param client        the Kubernetes client to use
     * @param namespace     the namespace to create the ConfigMap in
     * @param name          the name of the ConfigMap
     * @param configuration the content of the {@code config.xml} file
     * @return the created ConfigMap instance
     */
    public static @NotNull ConfigMap createConfigMap(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String name,
            final @NotNull String configuration) {
        return createConfigMap(client, namespace, name, Map.of("config.xml", configuration));
    }

    /**
     * Creates a {@link ConfigMap} with the given data.
     *
     * @param client    the Kubernetes client to use
     * @param namespace the namespace to create the ConfigMap in
     * @param name      the name of the ConfigMap
     * @param data      the data of the ConfigMap
     * @return the created ConfigMap instance
     */
    public static @NotNull ConfigMap createConfigMap(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String name,
            final @NotNull Map<String, String> data) {
        final var configMap = client.configMaps()
                .inNamespace(namespace)
                .resource(new ConfigMapBuilder().withNewMetadata().withName(name).endMetadata().withData(data).build())
                .create();
        assertThat(configMap).isNotNull();
        return configMap;
    }

    /**
     * Creates a Secret resource from the given parameters.
     *
     * @param client    the Kubernetes client to use
     * @param namespace the namespace to create the custom resource in
     * @param content   the data content to hold for the provided key param
     */
    public static void createSecret(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String name,
            final @NotNull Map<String, String> content) {
        final var secret =
                new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(content).build();
        assertThat(client.secrets().inNamespace(namespace).resource(secret).create()).isNotNull();
    }

    /**
     * Creates a Secret resource from the given parameters.
     *
     * @param client    the Kubernetes client to use
     * @param namespace the namespace to create the custom resource in
     * @param name      the name of the Secret to create
     * @param key       the key entry for the Secret
     * @param content   the data content to hold for the provided key param
     */
    public static void createSecret(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String name,
            final @NotNull String key,
            final @NotNull String content) {
        final var secret =
                new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(key, content).build();
        assertThat(client.secrets().inNamespace(namespace).resource(secret).create()).isNotNull();
    }

    /**
     * Creates a Secret from the given resource file on the classpath.
     *
     * @param client       the Kubernetes client to use
     * @param namespace    the namespace to create the custom resource in
     * @param resourceName the name of the resource file to use
     * @return the created Secret instance
     */
    public static @NotNull Secret createSecret(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String resourceName) {
        return loadResource(client, namespace, resourceName, Secret.class).create();
    }

    /**
     * Creates a Service Account in the given namespace.
     *
     * @param client             the Kubernetes client to use
     * @param namespace          the namespace to create the service account in
     * @param serviceAccountName the name of the service account to create
     */
    public static void createServiceAccount(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String serviceAccountName) {
        final var serviceAccount = client.resource(new ServiceAccountBuilder() //
                        .withNewMetadata().withNamespace(namespace).withName(serviceAccountName).endMetadata().build())
                .create();
        assertThat(serviceAccount).isNotNull();
    }

    /**
     * @param clazz Class object to get the namespace from.
     * @return Namespace generated from the class passed as argument, up to 63 characters.
     */
    public static @NotNull String getNamespaceName(final @NotNull Class<?> clazz) {
        final var namespace = clazz.getSimpleName().toLowerCase();
        if (namespace.length() > 63) {
            return namespace.substring(0, 63);
        }
        return namespace;
    }

    /**
     * @param clazz Class object to get the operator namespace from.
     * @return Namespace generated from the class passed as argument, up to 63 characters.
     */
    public static @NotNull String getOperatorNamespaceName(final @NotNull Class<?> clazz) {
        final var namespace = clazz.getSimpleName().toLowerCase();
        if (namespace.length() > 54) {
            return namespace.substring(0, 54);
        }
        return namespace + "-operator";
    }

    /**
     * Returns the HiveMQ container from the given {@link StatefulSetSpec}
     * instance.
     * <p>
     * This is safe to use and cannot return {@code null} after the custom resource validation (that asserts the
     * existence of the HiveMQ container).
     *
     * @param statefulSetSpec the {@link StatefulSetSpec} to retrieve the HiveMQ container from
     * @return the HiveMQ container
     */
    public static @NotNull Container getHiveMQContainer(final @NotNull StatefulSetSpec statefulSetSpec) {
        return statefulSetSpec.getTemplate().getSpec().getContainers().getFirst();
    }

    /**
     * Returns the expected container as per the given containerName from the given {@link StatefulSetSpec}
     * instance.
     * <p>
     * If the container with the given name is not found, a {@link java.util.NoSuchElementException} will be thrown.
     *
     * @param statefulSetSpec the {@link StatefulSetSpec} to retrieve the HiveMQ container from
     * @param containerName   the name of the container to look for
     * @return the container
     */
    public static @NotNull Container getContainer(
            final @NotNull StatefulSetSpec statefulSetSpec, final @NotNull String containerName) {
        return statefulSetSpec.getTemplate()
                .getSpec()
                .getContainers()
                .stream()
                .filter(container -> container.getName().equals(containerName))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns the expected init container with as per the given initContainerName from the given
     * {@link StatefulSetSpec}
     * instance.
     * <p>
     * If the init container with the given name is not found, a {@link java.util.NoSuchElementException} will be
     * thrown.
     *
     * @param statefulSetSpec   the {@link StatefulSetSpec} to retrieve the HiveMQ container from
     * @param initContainerName the name of the container to look for
     * @return the container
     */
    public static @NotNull Container getInitContainer(
            final @NotNull StatefulSetSpec statefulSetSpec,
            final @NotNull String initContainerName) {
        return statefulSetSpec.getTemplate()
                .getSpec()
                .getInitContainers()
                .stream()
                .filter(container -> container.getName().equals(initContainerName))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns the HiveMQ platform {@link Resource<GenericKubernetesResource>} from the namespace given by using
     * the Kubernetes client provided.
     *
     * @param client    the Kubernetes client to use
     * @param namespace the namespace to look up for the service to expose
     * @param name      the HiveMQ platform name to fetch
     * @return the HiveMQ platform {@link Resource<GenericKubernetesResource>}
     */
    public static @NotNull Resource<GenericKubernetesResource> getHiveMQPlatform(
            final @NotNull KubernetesClient client, final @NotNull String namespace, final @NotNull String name) {
        final var context = new ResourceDefinitionContext.Builder().withGroup("hivemq.com")
                .withKind("HiveMQPlatform")
                .withVersion("v1")
                .withPlural("hivemq-platforms")
                .withNamespaced(true)
                .build();
        return client.genericKubernetesResources(context).inNamespace(namespace).withName(name);
    }

    /**
     * Returns some of the fixed default labels defined for a Platform Operator pod.
     *
     * @param releaseName the release name of the Platform Operator chart
     * @return {@link Map}  Map containing some of the fixed labels expected for a Platform Operator pod.
     */
    public static @NotNull Map<String, String> getHiveMQPlatformOperatorLabels(final @NotNull String releaseName) {
        return Map.of("app.kubernetes.io/instance", releaseName, "app.kubernetes.io/name", "hivemq-platform-operator");
    }

    /**
     * Returns some of the fixed default labels defined for Platform pod.
     *
     * @param releaseName the release name of the Platform chart
     * @return {@link Map}  Map containing some of the fixed labels expected for a Platform pod.
     */
    public static @NotNull Map<String, String> getHiveMQPlatformLabels(final @NotNull String releaseName) {
        return Map.of("app.kubernetes.io/instance",
                releaseName,
                "app.kubernetes.io/name",
                "hivemq-platform",
                "hivemq-platform",
                releaseName);
    }

    /**
     * Returns a Predicate condition based on the state of a Kubernetes resource.
     */
    @SuppressWarnings("unchecked")
    public static @NotNull Predicate<GenericKubernetesResource> getHiveMQPlatformStatus(final @NotNull String state) {
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
     * Returns the {@link Container} container from the given {@link StatefulSetSpec}
     * instance.
     *
     * @param client          the Kubernetes client to use
     * @param namespace       the namespace to use to fetch the statefulSet from
     * @param statefulSetName the name of the statefulSet to fetch
     */
    public static @NotNull StatefulSet getStatefulSet(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String statefulSetName) {
        return client.apps().statefulSets().inNamespace(namespace).withName(statefulSetName).get();
    }

    /**
     * Updates a ConfigMap from the given resource file on the classpath.
     *
     * @param client       the Kubernetes client to use
     * @param namespace    the namespace to update the custom resource in
     * @param resourceName the name of the resource file to use
     */
    public static void updateConfigMap(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String resourceName) {
        loadResource(client, namespace, resourceName, ConfigMap.class).update();
    }

    /**
     * Waits for the Operator pod based on the given name to be in a running status.
     */
    public static void waitForHiveMQOperatorPodStateRunning(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String releaseName) {
        waitForPodStateRunning(client, namespace, getHiveMQPlatformOperatorLabels(releaseName));
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
                .waitUntilCondition(pod -> pod.getStatus()
                                .getContainerStatuses()
                                .stream()
                                .allMatch(containerStatus -> containerStatus.getReady() && containerStatus.getStarted()),
                        3,
                        TimeUnit.MINUTES);
    }

    /**
     * Waits for the given platform to be in a RUNNING status.
     */
    public static @NotNull GenericKubernetesResource waitForHiveMQPlatformStateRunning(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName) {
        return waitForHiveMQPlatformState(client, namespace, customResourceName, "RUNNING");
    }

    /**
     * Waits for the given platform to be in a specific status.
     */
    public static @NotNull GenericKubernetesResource waitForHiveMQPlatformState(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName,
            final @NotNull String state) {
        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, namespace, customResourceName);
        hivemqCustomResource.waitUntilCondition(getHiveMQPlatformStatus(state), 5, TimeUnit.MINUTES);
        assertThat(hivemqCustomResource.get().get("status").toString()).contains(state);
        return hivemqCustomResource.get();
    }

    /**
     * Asserts that the given MQTT service is of type ClusterIP.
     *
     * @param client          the Kubernetes client to use
     * @param namespace       the namespace to wait to check for all the pods to be removed
     * @param mqttServiceName the name of the MQTT service to assert
     */
    public static void assertMqttService(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName) {
        await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
            final var services = client.services().inNamespace(namespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> mqttServiceName.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("ClusterIP");
        });
    }

    private static <T extends HasMetadata> @NotNull Resource<T> loadResource(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String resourceName,
            final @NotNull Class<T> clazz) {
        try (final InputStream is = K8sUtil.class.getClassLoader().getResourceAsStream(resourceName)) {
            final var resource = client.resources(clazz).load(is);
            assertThat(resource).isNotNull();
            resource.item().getMetadata().setNamespace(namespace);
            return resource;
        } catch (final IOException e) {
            throw new AssertionError("Could not read resource " + resourceName + ": " + e.getMessage());
        }
    }
}
