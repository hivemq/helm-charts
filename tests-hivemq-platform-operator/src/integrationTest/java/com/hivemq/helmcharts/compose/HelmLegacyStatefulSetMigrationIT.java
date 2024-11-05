package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.ControlCenterUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MonitoringUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import com.hivemq.helmcharts.util.RestAPIUtil;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hivemq.helmcharts.testcontainer.DockerImageNames.SELENIUM_DOCKER_IMAGE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the migration of a legacy HiveMQ Cluster StatefulSet, started with HiveMQ Legacy Operator,
 * to a HiveMQ Platform StatefulSet using the current operator version.
 * <p>
 * The different steps performed to properly migrate an existing Legacy HiveMQ Operator chart are:
 * <ol>
 *  <li>Install HiveMQ Platform Operator chart.</li>
 *  <li>Scale down to zero Legacy HiveMQ Operator.</li>
 *  <li>Delete HiveMQ Cluster CustomResource with orphan.</li>
 *  <li>Scale up Legacy HiveMQ Operator if needed.</li>
 *  <li>Uninstall Legacy HiveMQ Operator Helm chart with cascade=orphan.</li>
 *  <li>Create the corresponding ConfigMap for the config.xml file of the broker.</li>
 *  <li>Install the HiveMQ Platform Helm chart with same release name as Legacy HiveMQ Operator chart</li>
 * </ol>
 */
@Tag("Migration")
@Testcontainers
class HelmLegacyStatefulSetMigrationIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT = 1883;
    private static final @NotNull String MQTT_SERVICE_NAME = String.format("hivemq-%s-mqtt", LEGACY_RELEASE_NAME);
    private static final int CC_SERVICE_PORT = 8080;
    private static final @NotNull String CC_SERVICE_NAME = String.format("hivemq-%s-cc", LEGACY_RELEASE_NAME);
    private static final int METRICS_SERVICE_PORT = 9399;
    private static final @NotNull String METRICS_SERVICE_NAME = String.format("hivemq-%s-metrics", LEGACY_RELEASE_NAME);
    private static final int REST_API_SERVICE_PORT = 8888;
    private static final @NotNull String REST_API_SERVICE_NAME = String.format("hivemq-%s-api", LEGACY_RELEASE_NAME);
    private static final @NotNull String CLUSTER_SERVICE_NAME = String.format("hivemq-%s-cluster", LEGACY_RELEASE_NAME);

    @Container
    @SuppressWarnings("resource")
    private static final @NotNull BrowserWebDriverContainer<?> WEB_DRIVER_CONTAINER =
            new BrowserWebDriverContainer<>(SELENIUM_DOCKER_IMAGE) //
                    .withNetwork(network) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway") //
                    .withCapabilities(new ChromeOptions());

    @Override
    protected boolean uninstallPlatformChart() {
        return false;
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        helmChartContainer.uninstallRelease(LEGACY_RELEASE_NAME, operatorNamespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void migrateToCurrentPlatformOperator() throws Exception {
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-legacy-config-map.yml");
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-file-realm-config-map.yml");
        installLegacyOperatorChartAndWaitToBeRunning("/files/migration-legacy-stateful-set-values.yaml");

        // assert service annotations and labels
        final var mqttAnnotations = new HashMap<>(Map.of("service.spec.externalTrafficPolicy", "Local"));
        final var metricsAnnotations = new HashMap<>(Map.of("prometheus.io/scrape", "true"));
        final var legacyLabels = Map.of("app", "hivemq", "hivemq-cluster", LEGACY_RELEASE_NAME);
        assertAnnotationsAndLabels("MQTT Service",
                () -> client.services().inNamespace(operatorNamespace).withName(MQTT_SERVICE_NAME).get().getMetadata(),
                mqttAnnotations,
                legacyLabels,
                true,
                true);
        assertAnnotationsAndLabels("Control Center Service",
                () -> client.services().inNamespace(operatorNamespace).withName(CC_SERVICE_NAME).get().getMetadata(),
                Map.of(),
                legacyLabels,
                true,
                true);
        assertAnnotationsAndLabels("Metrics Service",
                () -> client.services()
                        .inNamespace(operatorNamespace)
                        .withName(METRICS_SERVICE_NAME)
                        .get()
                        .getMetadata(),
                metricsAnnotations,
                legacyLabels,
                true,
                true);
        assertAnnotationsAndLabels("REST API Service",
                () -> client.services()
                        .inNamespace(operatorNamespace)
                        .withName(REST_API_SERVICE_NAME)
                        .get()
                        .getMetadata(),
                Map.of(),
                legacyLabels,
                true,
                true);
        assertAnnotationsAndLabels("Cluster Service",
                () -> client.services()
                        .inNamespace(operatorNamespace)
                        .withName(CLUSTER_SERVICE_NAME)
                        .get()
                        .getMetadata(),
                Map.of(),
                legacyLabels,
                true,
                true);
        final var hiveMQVersion = System.getProperty("hivemq.version", "latest");
        final var legacyStatefulSetLabels = new HashMap<>(Map.of("app.kubernetes.io/instance",
                LEGACY_RELEASE_NAME,
                "app.kubernetes.io/managed-by",
                "Helm",
                "app.kubernetes.io/name",
                "hivemq-operator",
                "app.kubernetes.io/version",
                hiveMQVersion));
        legacyStatefulSetLabels.putAll(legacyLabels);
        assertAnnotationsAndLabels("StatefulSet",
                () -> client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(LEGACY_RELEASE_NAME)
                        .get()
                        .getMetadata(),
                Map.of(),
                legacyStatefulSetLabels,
                false,
                true);
        final var legacyStatefulSetTemplateLabels = new HashMap<>(Map.of("hivemq.com/node-offline", "false"));
        legacyStatefulSetTemplateLabels.putAll(legacyLabels);
        assertAnnotationsAndLabels("StatefulSet Template",
                () -> client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(LEGACY_RELEASE_NAME)
                        .get()
                        .getSpec()
                        .getTemplate()
                        .getMetadata(),
                Map.of(),
                legacyStatefulSetTemplateLabels,
                true,
                true);
        // assert Control Center login
        ControlCenterUtil.assertLogin(client,
                operatorNamespace,
                WEB_DRIVER_CONTAINER,
                CC_SERVICE_NAME,
                CC_SERVICE_PORT);
        // publish retained messages
        final var topicList =
                MqttUtil.publishRetainedMessages(client, operatorNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);
        // assert RestAPI service and authorization with ESE extension
        RestAPIUtil.assertAuth(client, operatorNamespace, REST_API_SERVICE_NAME, REST_API_SERVICE_PORT);
        // assert publishes metrics
        MonitoringUtil.assertPublishesMetrics(client, operatorNamespace, METRICS_SERVICE_NAME, METRICS_SERVICE_PORT);

        // scale down the legacy operator
        // kubectl scale deployment <release-name>-hivemq-operator-operator --replicas=0 -n <namespace>
        final var legacyOperatorDeploymentName = String.format("%s-hivemq-operator-operator", LEGACY_RELEASE_NAME);
        K8sUtil.scaleDeployment(client, operatorNamespace, legacyOperatorDeploymentName, 0);
        var legacyOperatorLabels = K8sUtil.getHiveMQLegacyOperatorLabels(LEGACY_RELEASE_NAME);
        client.pods()
                .inNamespace(operatorNamespace)
                .withLabels(legacyOperatorLabels)
                .waitUntilCondition(Objects::isNull, 1, TimeUnit.MINUTES);
        assertThat(client.pods()
                .inNamespace(operatorNamespace)
                .withLabels(legacyOperatorLabels)
                .list()
                .getItems()).isEmpty();

        // delete custom resource with orphan
        // kubectl delete hivemq-clusters.hivemq.com <release-name> --cascade=orphan -n <namespace>
        final var legacyPlatform = K8sUtil.getLegacyHiveMQPlatform(client, operatorNamespace, LEGACY_RELEASE_NAME);
        legacyPlatform.withPropagationPolicy(DeletionPropagation.ORPHAN).withTimeoutInMillis(5000).delete();
        assertThat(K8sUtil.getLegacyHiveMQPlatform(client, operatorNamespace, LEGACY_RELEASE_NAME).get()).isNull();

        // scale up the legacy operator
        // kubectl scale deployment <release-name>-hivemq-operator-operator --replicas=1 -n <namespace>
        K8sUtil.scaleDeployment(client, operatorNamespace, legacyOperatorDeploymentName, 1);
        K8sUtil.waitForLegacyOperatorPodStateRunning(client, operatorNamespace, LEGACY_RELEASE_NAME);
        assertThat(client.pods()
                .inNamespace(operatorNamespace)
                .withLabels(legacyOperatorLabels)
                .list()
                .getItems()
                .size()).isEqualTo(1);

        // we need to uninstall the legacy release with orphan option so existing resources can be taken over by the platform Helm install
        // TODO: We cannot execute `helm uninstall my-release --cascade orphan` because this will delete resources
        //  without an owner reference, that are still needed - See: https://github.com/helm/helm/issues/13279
        //helmChartContainer.uninstallRelease(LEGACY_RELEASE_NAME, operatorNamespace, "--cascade", "orphan");

        // as a workaround we need to delete all the corresponding Helm versioned Secrets installed for each of the possible Helm releases
        // we may have. These will be named with a format like "sh.helm.release.v1.<release-name>.v<release-version>" and we will delete them
        // based on the labels instead as follows:
        // kubectl delete secret -l owner=helm,name=<release-name> -n <namespace>
        final var helmSecretLabels = Map.of("owner", "helm", "name", LEGACY_RELEASE_NAME);
        client.secrets().inNamespace(operatorNamespace).withLabels(helmSecretLabels).withTimeoutInMillis(5000).delete();
        assertThat(client.secrets()
                .inNamespace(operatorNamespace)
                .withLabels(helmSecretLabels)
                .list()
                .getItems()).isEmpty();

        // if a legacy configuration for a extension is in place, we need to update the existing
        //  ConfigMap by adding a new entry for `config.xml`.
        K8sUtil.updateConfigMap(client, operatorNamespace, "ese-legacy-config-map-update.yml");

        helmChartContainer.installPlatformChart(LEGACY_RELEASE_NAME,
                "-f",
                "/files/migration-platform-stateful-set-values.yaml",
                "--namespace",
                operatorNamespace);

        K8sUtil.waitForHiveMQPlatformState(client, operatorNamespace, LEGACY_RELEASE_NAME, "STATEFULSET_MIGRATION");
        K8sUtil.waitForHiveMQPlatformState(client, operatorNamespace, LEGACY_RELEASE_NAME, "ROLLING_RESTART");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, operatorNamespace, LEGACY_RELEASE_NAME);

        // assert service annotations and labels
        final var platformLabels = Map.of("app.kubernetes.io/instance",
                LEGACY_RELEASE_NAME,
                "app.kubernetes.io/managed-by",
                "Helm",
                "app.kubernetes.io/name",
                "hivemq-platform",
                "app.kubernetes.io/version",
                hiveMQVersion,
                "hivemq-platform",
                LEGACY_RELEASE_NAME);
        final var platformServiceLabels = new HashMap<String, String>(platformLabels.size() + 1);
        platformServiceLabels.putAll(platformLabels);
        platformServiceLabels.put("hivemq/platform-service", "true");
        final var helmAnnotations = Map.of("meta.helm.sh/release-name",
                LEGACY_RELEASE_NAME,
                "meta.helm.sh/release-namespace",
                operatorNamespace);
        mqttAnnotations.putAll(helmAnnotations);
        assertAnnotationsAndLabels("MQTT Service",
                () -> client.services().inNamespace(operatorNamespace).withName(MQTT_SERVICE_NAME).get().getMetadata(),
                mqttAnnotations,
                platformServiceLabels,
                true,
                true);
        assertAnnotationsAndLabels("Control Center Service",
                () -> client.services().inNamespace(operatorNamespace).withName(CC_SERVICE_NAME).get().getMetadata(),
                helmAnnotations,
                platformServiceLabels,
                true,
                true);
        metricsAnnotations.putAll(helmAnnotations);
        assertAnnotationsAndLabels("Metrics Service",
                () -> client.services()
                        .inNamespace(operatorNamespace)
                        .withName(METRICS_SERVICE_NAME)
                        .get()
                        .getMetadata(),
                metricsAnnotations,
                platformServiceLabels,
                true,
                true);
        assertAnnotationsAndLabels("REST API Service",
                () -> client.services()
                        .inNamespace(operatorNamespace)
                        .withName(REST_API_SERVICE_NAME)
                        .get()
                        .getMetadata(),
                helmAnnotations,
                platformServiceLabels,
                true,
                true);
        final var clusterServiceLabels = new HashMap<String, String>(platformLabels.size() + 1);
        clusterServiceLabels.putAll(platformLabels);
        clusterServiceLabels.put("hivemq/cluster-service", "true");
        assertAnnotationsAndLabels("Cluster Service",
                () -> client.services()
                        .inNamespace(operatorNamespace)
                        .withName(CLUSTER_SERVICE_NAME)
                        .get()
                        .getMetadata(),
                helmAnnotations,
                clusterServiceLabels,
                true,
                true);
        assertAnnotationsAndLabels("StatefulSet",
                () -> client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(LEGACY_RELEASE_NAME)
                        .get()
                        .getMetadata(),
                helmAnnotations,
                platformLabels,
                true,
                true);
        assertAnnotationsAndLabels("StatefulSet Template",
                () -> client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(LEGACY_RELEASE_NAME)
                        .get()
                        .getSpec()
                        .getTemplate()
                        .getMetadata(),
                helmAnnotations,
                platformLabels,
                true,
                true);
        // assert again Control Center login
        ControlCenterUtil.assertLogin(client,
                operatorNamespace,
                WEB_DRIVER_CONTAINER,
                CC_SERVICE_NAME,
                CC_SERVICE_PORT);
        // assert retained messages
        MqttUtil.assertRetainedMessages(client, operatorNamespace, topicList, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);
        // assert RestAPI service and authorization with ESE extension
        RestAPIUtil.assertAuth(client, operatorNamespace, REST_API_SERVICE_NAME, REST_API_SERVICE_PORT);
        // assert subscribe and publishes metrics
        MonitoringUtil.assertSubscribesMetrics(client, operatorNamespace, METRICS_SERVICE_NAME, METRICS_SERVICE_PORT);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertAnnotationsAndLabels(
            final @NotNull String label,
            final @NotNull Supplier<ObjectMeta> metadataSupplier,
            final @NotNull Map<String, String> expectedAnnotations,
            final @NotNull Map<String, String> expectedLabels,
            final boolean expectedAnnotationsToBeEqual,
            final boolean expectedLabelsToBeEqual) {
        final var metadata = metadataSupplier.get();
        final var actualAnnotations = metadata.getAnnotations()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().contains("javaoperatorsdk.io") &&
                        !entry.getKey().contains("jobLabel") &&
                        !entry.getKey().contains("kubernetes-resource-versions"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (expectedAnnotationsToBeEqual) {
            assertThat(actualAnnotations).as(String.format("%s annotations", label)).isEqualTo(expectedAnnotations);
        } else {
            assertThat(actualAnnotations).as(String.format("%s annotations", label)).isNotEqualTo(expectedAnnotations);
        }
        final var actualLabels = metadata.getLabels()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().contains("helm.sh/chart") && !entry.getKey().contains("jobLabel"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (expectedLabelsToBeEqual) {
            assertThat(actualLabels).as(String.format("%s labels", label)).isEqualTo(expectedLabels);
        } else {
            assertThat(actualLabels).as(String.format("%s labels", label)).isNotEqualTo(expectedLabels);
        }
    }
}
