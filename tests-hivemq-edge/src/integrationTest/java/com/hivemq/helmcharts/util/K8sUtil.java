package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

public class K8sUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(K8sUtil.class);

    private K8sUtil() {
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
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var services = client.services().inNamespace(namespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> mqttServiceName.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("ClusterIP");
        });
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
    @SuppressWarnings("UnusedReturnValue")
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
     * @return the created Secret instance
     */
    public static @NotNull Secret createSecret(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String name,
            final @NotNull Map<String, String> content) {
        final var secret =
                new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(content).build();
        assertThat(client.secrets().inNamespace(namespace).resource(secret).create()).isNotNull();
        return secret;
    }

    /**
     * Creates a Secret resource from the given parameters.
     *
     * @param client    the Kubernetes client to use
     * @param namespace the namespace to create the custom resource in
     * @param name      the name of the Secret to create
     * @param key       the key entry for the Secret
     * @param content   the encoded base64 data content to hold for the provided key param
     * @return the created Secret instance
     */
    public static @NotNull Secret createSecret(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String name,
            final @NotNull String key,
            final @NotNull String content) {
        final var secret =
                new SecretBuilder().withNewMetadata().withName(name).endMetadata().addToData(key, content).build();
        assertThat(client.secrets().inNamespace(namespace).resource(secret).create()).isNotNull();
        return secret;
    }

    /**
     * Creates a Secret from the given resource file on the classpath.
     *
     * @param client       the Kubernetes client to use
     * @param namespace    the namespace to create the custom resource in
     * @param resourceName the name of the Secret resource file to use
     * @return the created Secret instance
     */
    public static @NotNull Secret createSecret(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String resourceName) {
        return loadResource(client, namespace, resourceName, Secret.class).create();
    }

    /**
     * Creates a {@link Secret} with a {@code config.xml} entry with the given non-encoded configuration.
     *
     * @param client        the Kubernetes client to use
     * @param namespace     the namespace to create the Secret in
     * @param name          the name of the Secret
     * @param configuration the non-encoded content of the {@code config.xml} file
     * @return the created Secret instance
     */
    public static @NotNull Secret createSecret(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String name,
            final @NotNull String configuration) {
        return createSecret(client,
                namespace,
                name,
                Map.of("config.xml",
                        Base64.getEncoder().encodeToString(configuration.getBytes(StandardCharsets.UTF_8))));
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
     * Executes the the given command in the HiveMQ platform pod for the HiveMQ container
     * and asserts the output, error results and exit code
     *
     * @param client    the Kubernetes client to use
     * @param namespace the namespace to create the service account in
     * @param pod       the pod to execute the command in
     * @param command   the command line to execute
     */
    public static void executeInHiveMQPod(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull Pod pod,
            final @NotNull String... command) {
        executeInPod(client, namespace, pod, "hivemq", command);
    }

    /**
     * Executes the the given command in the specified pod and asserts the output,
     * error results and exit code
     *
     * @param client        the Kubernetes client to use
     * @param namespace     the namespace to create the service account in
     * @param pod           the pod to execute the command in
     * @param containerName the container to execute the command in
     * @param command       the command line to execute
     */
    public static void executeInPod(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull Pod pod,
            final @NotNull String containerName,
            final @NotNull String... command) {
        final var execResult = PodUtil.execute(client, namespace, pod.getMetadata().getName(), containerName, command);
        try {
            assertThat(execResult.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(execResult.getOutput()).as("stdout: %s", execResult.getOutput()).isNull();
            assertThat(execResult.getError()).as("stderr: %s", execResult.getError()).isNull();
            assertThat(execResult.exitCode()).isNotNull().isEqualTo(0);
        } catch (final Exception e) {
            fail("Could not execute '%s' in Pod '%s': %s", command, pod.getMetadata().getName(), e);
        } finally {
            execResult.close();
        }
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
     * Returns the default labels defined for a legacy Operator pod.
     *
     * @param releaseName the release name of the legacy Operator chart
     * @return {@link Map}  Map containing some of the fixed labels expected for a legacy Operator pod.
     */
    public static @NotNull Map<String, String> getHiveMQLegacyOperatorLabels(final @NotNull String releaseName) {
        return Map.of("app", "hivemq-operator", "operator", String.format("%s-hivemq-operator", releaseName));
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
     * Returns the default labels defined for Platform pod.
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
     * Returns the default labels defined for a Platform Operator pod.
     *
     * @param releaseName the release name of the Platform Operator chart
     * @return {@link Map}  Map containing some of the fixed labels expected for a Platform Operator pod.
     */
    public static @NotNull Map<String, String> getHiveMQPlatformOperatorLabels(final @NotNull String releaseName) {
        return Map.of("app.kubernetes.io/instance", releaseName, "app.kubernetes.io/name", "hivemq-platform-operator");
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
            final @NotNull StatefulSetSpec statefulSetSpec, final @NotNull String initContainerName) {
        return statefulSetSpec.getTemplate()
                .getSpec()
                .getInitContainers()
                .stream()
                .filter(container -> container.getName().equals(initContainerName))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns a Predicate condition based on the state of a Kubernetes resource.
     */
    @SuppressWarnings("unchecked")
    public static @NotNull Predicate<GenericKubernetesResource> getCustomResourceStateCondition(final @NotNull String expectedState) {
        return resource -> {
            if (resource == null) {
                return false;
            }
            final var status = (Map<String, String>) resource.getAdditionalProperties().get("status");
            if (status == null) {
                return false;
            }
            final var actualState = status.get("state");
            if (actualState == null || actualState.isBlank()) {
                return false;
            }
            LOG.debug("Comparing actual state '{}' and waiting for '{}'", actualState, expectedState);
            return actualState.contains(expectedState);
        };
    }

    /**
     * Returns the HiveMQ cluster {@link Resource<GenericKubernetesResource>} from the namespace given by using
     * the Kubernetes client provided.
     *
     * @param client    the Kubernetes client to use
     * @param namespace the namespace to look up for the service to expose
     * @param name      the HiveMQ cluster name to fetch
     * @return the HiveMQ platform {@link Resource<GenericKubernetesResource>}
     */
    public static @NotNull Resource<GenericKubernetesResource> getLegacyHiveMQPlatform(
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
     * @param clazz Class object to get the namespace from.
     * @return Namespace generated from the class passed as argument, up to 63 characters.
     */
    public static @NotNull String getNamespaceName(final @NotNull Class<?> clazz) {
        return getNamespaceName(clazz, "");
    }

    private static @NotNull String getNamespaceName(final @NotNull Class<?> clazz, final @NotNull String suffix) {
        final var maxLength = 63 - suffix.length();
        final var namespace = clazz.getSimpleName().toLowerCase();
        if (namespace.length() > maxLength) {
            return namespace.substring(0, maxLength) + suffix;
        }
        return namespace + suffix;
    }

    /**
     * @param clazz Class object to get the operator namespace from.
     * @return Namespace generated from the class passed as argument, up to 63 characters.
     */
    public static @NotNull String getOperatorNamespaceName(final @NotNull Class<?> clazz) {
        return getNamespaceName(clazz, "-operator");
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
     * Returns the {@link StatefulSet} instance for the given namespace and name.
     *
     * @param client          the Kubernetes client to use
     * @param namespace       the namespace to use to fetch the statefulSet from
     * @param statefulSetName the name of the statefulSet to fetch
     */
    public static @NotNull StatefulSet getStatefulSet(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String statefulSetName) {
        final var statefulSet = client.apps().statefulSets().inNamespace(namespace).withName(statefulSetName).get();
        assertThat(statefulSet).isNotNull();
        return statefulSet;
    }

    /**
     * Returns the {@link Deployment} instance for the given namespace and name.
     *
     * @param client         the Kubernetes client to use
     * @param namespace      the namespace to use to fetch the deployment from
     * @param deploymentName the name of the deployment to fetch
     */
    public static @NotNull Deployment getDeployment(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String deploymentName) {
        final var deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
        assertThat(deployment).isNotNull();
        return deployment;
    }

    /**
     * Scales a {@link Deployment} to the given number of replicas.
     *
     * @param client         the Kubernetes client to use
     * @param namespace      the namespace to use to scale the deployment from
     * @param deploymentName the name of the deployment to scale
     * @param replicas       the number of replicas to scale
     */
    public static void scaleDeployment(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String deploymentName,
            final int replicas) {
        final var deployment = client.apps().deployments().inNamespace(namespace).withName(deploymentName).get();
        deployment.getSpec().setReplicas(replicas);
        client.resource(deployment).update();
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
     * Waits for the given HiveMQ Platform to be in a specific status.
     */
    public static @NotNull GenericKubernetesResource waitForHiveMQPlatformState(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName,
            final @NotNull String state) {
        final var hivemqCustomResource = getHiveMQPlatform(client, namespace, customResourceName);
        hivemqCustomResource.waitUntilCondition(getCustomResourceStateCondition(state), 5, TimeUnit.MINUTES);
        assertThat(hivemqCustomResource.get().get("status").toString()).contains(state);
        return hivemqCustomResource.get();
    }

    /**
     * Waits for the given HiveMQ Platform to be in a RUNNING status.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static @NotNull GenericKubernetesResource waitForHiveMQPlatformStateRunning(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName) {
        return waitForHiveMQPlatformState(client, namespace, customResourceName, "RUNNING");
    }

    @SuppressWarnings("UnusedReturnValue")
    public static @NotNull GenericKubernetesResource waitForHiveMQPlatformStateRunningAfterRollingRestart(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName) {
        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, namespace, customResourceName);
        assertThat(hivemqCustomResource).isNotNull();
        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("ROLLING_RESTART"),
                5,
                TimeUnit.MINUTES);
        return hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RUNNING"),
                5,
                TimeUnit.MINUTES);
    }

    /**
     * Waits for the given HiveMQ Cluster to be in a specific status.
     */
    public static @NotNull GenericKubernetesResource waitForLegacyHiveMQPlatformState(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName,
            final @NotNull String state) {
        final var hivemqCustomResource = getLegacyHiveMQPlatform(client, namespace, customResourceName);
        hivemqCustomResource.waitUntilCondition(getCustomResourceStateCondition(state), 5, TimeUnit.MINUTES);
        assertThat(hivemqCustomResource.get().get("status").toString()).contains(state);
        return hivemqCustomResource.get();
    }

    /**
     * Waits for the given HiveMQ Cluster to be in a RUNNING status.
     */
    @SuppressWarnings("UnusedReturnValue")
    public static @NotNull GenericKubernetesResource waitForLegacyHiveMQPlatformStateRunning(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String customResourceName) {
        return waitForLegacyHiveMQPlatformState(client, namespace, customResourceName, "Running");
    }

    /**
     * Waits for the legacy Operator pods based on the given name to be in a running status.
     */
    public static void waitForLegacyOperatorPodStateRunning(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String releaseName) {
        waitForPodStateRunning(client, namespace, getHiveMQLegacyOperatorLabels(releaseName));
    }

    /**
     * Waits for the Platform Operator pod based on the given name to be in a running status.
     */
    public static void waitForPlatformOperatorPodStateRunning(
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

    private static <T extends HasMetadata> @NotNull Resource<T> loadResource(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String resourceName,
            final @NotNull Class<T> clazz) {
        try (final var inputStream = K8sUtil.class.getClassLoader().getResourceAsStream(resourceName)) {
            final var resource = client.resources(clazz).load(inputStream);
            assertThat(resource).isNotNull();
            resource.item().getMetadata().setNamespace(namespace);
            return resource;
        } catch (final IOException e) {
            throw new AssertionError("Could not read resource " + resourceName + ": " + e.getMessage());
        }
    }
}
