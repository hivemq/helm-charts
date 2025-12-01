package com.hivemq.helmcharts.migration;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.ControlCenterUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MonitoringUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import com.hivemq.helmcharts.util.RestAPIUtil;
import com.marcnuri.helm.Release;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.github.sgtsilvio.gradle.oci.junit.jupiter.OciImages;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.selenium.BrowserWebDriverContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the migration of a legacy HiveMQ Cluster StatefulSet, started with HiveMQ Legacy Operator,
 * to a HiveMQ Platform StatefulSet using the current operator version.
 * <p>
 * The different steps performed to properly migrate an existing Legacy HiveMQ Operator chart are:
 * <ol>
 *  <li>Install HiveMQ Platform Operator chart.</li>
 *  <li>Scale down to zero Legacy HiveMQ Operator.</li>
 *  <li>Delete HiveMQ Cluster CustomResource with {@code cascade=orphan}.</li>
 *  <li>Scale up Legacy HiveMQ Operator if needed.</li>
 *  <li>Uninstall Legacy HiveMQ Operator Helm chart with {@code cascade=orphan}.</li>
 *  <li>Create the corresponding ConfigMap for the config.xml file of the broker.</li>
 *  <li>Install the HiveMQ Platform Helm chart with same release name as Legacy HiveMQ Operator chart.</li>
 * </ol>
 */
@Testcontainers
class HelmLegacyStatefulSetMigrationIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT = 1883;
    private static final @NotNull String MQTT_SERVICE_NAME = "hivemq-%s-mqtt".formatted(LEGACY_RELEASE_NAME);
    private static final int CC_SERVICE_PORT = 8080;
    private static final @NotNull String CC_SERVICE_NAME = "hivemq-%s-cc".formatted(LEGACY_RELEASE_NAME);
    private static final int METRICS_SERVICE_PORT = 9399;
    private static final @NotNull String METRICS_SERVICE_NAME = "hivemq-%s-metrics".formatted(LEGACY_RELEASE_NAME);
    private static final int REST_API_SERVICE_PORT = 8888;
    private static final @NotNull String REST_API_SERVICE_NAME = "hivemq-%s-api".formatted(LEGACY_RELEASE_NAME);
    private static final @NotNull String CLUSTER_SERVICE_NAME = "hivemq-%s-cluster".formatted(LEGACY_RELEASE_NAME);

    @Container
    private static final @NotNull BrowserWebDriverContainer WEB_DRIVER_CONTAINER =
            new BrowserWebDriverContainer(OciImages.getImageName("selenium/standalone-firefox")) //
                    .withNetwork(network) //
                    // needed for Docker on Linux
                    .withExtraHost("host.docker.internal", "host-gateway");

    @Override
    protected boolean uninstallPlatformChart() {
        return false;
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() {
        helmUninstallLegacyOperator.call();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "LATEST",
                             disabledReason = "there are various PVC related bugfixes in K8s that probably cause issues with older versions")
    void migrateToCurrentPlatformOperator() throws Exception {
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-legacy-config-map.yml");
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-file-realm-config-map.yml");
        final var release = helmUpgradeLegacyOperator.withValuesFile(VALUES_PATH.resolve("migration-legacy-stateful-set-values.yaml")).call();
        assertThat(release).returns("deployed", Release::getStatus);
        K8sUtil.waitForLegacyOperatorPodStateRunning(client, operatorNamespace, LEGACY_RELEASE_NAME);
        K8sUtil.waitForLegacyHiveMQPlatformStateRunning(client, operatorNamespace, LEGACY_RELEASE_NAME);

        // assert service annotations and labels
        final var hiveMQVersion = System.getProperty("hivemq.tag", "latest");
        final var mqttAnnotations = Map.of("service.spec.externalTrafficPolicy", "Local");
        final var metricsAnnotations = Map.of("prometheus.io/scrape", "true");
        final var legacyLabels = Map.of("app", "hivemq", "hivemq-cluster", LEGACY_RELEASE_NAME);
        assertAnnotationsAndLabels("Legacy MQTT Service",
                client.services().inNamespace(operatorNamespace).withName(MQTT_SERVICE_NAME).get().getMetadata(),
                mqttAnnotations,
                legacyLabels);
        assertAnnotationsAndLabels("Legacy Control Center Service",
                client.services().inNamespace(operatorNamespace).withName(CC_SERVICE_NAME).get().getMetadata(),
                Map.of(),
                legacyLabels);
        assertAnnotationsAndLabels("Legacy Metrics Service",
                client.services().inNamespace(operatorNamespace).withName(METRICS_SERVICE_NAME).get().getMetadata(),
                metricsAnnotations,
                legacyLabels);
        assertAnnotationsAndLabels("Legacy REST API Service",
                client.services().inNamespace(operatorNamespace).withName(REST_API_SERVICE_NAME).get().getMetadata(),
                Map.of(),
                legacyLabels);
        assertAnnotationsAndLabels("Legacy Cluster Service",
                client.services().inNamespace(operatorNamespace).withName(CLUSTER_SERVICE_NAME).get().getMetadata(),
                Map.of(),
                legacyLabels);
        final var legacyStatefulSetMetadata = client.apps()
                .statefulSets()
                .inNamespace(operatorNamespace)
                .withName(LEGACY_RELEASE_NAME)
                .get()
                .getMetadata();
        assertThat(legacyStatefulSetMetadata.getAnnotations()).containsOnlyKeys("hivemq.com/resource-spec",
                "kubernetes.io/change-cause");
        final var legacyChartVersion = legacyChart.getChartVersion();
        assertAnnotationsAndLabels("Legacy StatefulSet",
                legacyStatefulSetMetadata,
                legacyStatefulSetMetadata.getAnnotations(),
                mergeMaps(legacyLabels,
                        Map.of("app.kubernetes.io/instance",
                                LEGACY_RELEASE_NAME,
                                "app.kubernetes.io/managed-by",
                                "Helm",
                                "app.kubernetes.io/name",
                                "hivemq-operator",
                                "app.kubernetes.io/version",
                                hiveMQVersion,
                                "helm.sh/chart",
                                "hivemq-operator-%s".formatted(legacyChartVersion),
                                "hivemq-cluster",
                                LEGACY_RELEASE_NAME)));
        assertAnnotationsAndLabels("Legacy StatefulSet Template",
                client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(LEGACY_RELEASE_NAME)
                        .get()
                        .getSpec()
                        .getTemplate()
                        .getMetadata(),
                Map.of(),
                mergeMaps(legacyLabels, Map.of("hivemq.com/node-offline", "false")));
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
        RestAPIUtil.assertAuth(client, operatorNamespace, REST_API_SERVICE_NAME, REST_API_SERVICE_PORT, false);
        // assert publishes metrics
        MonitoringUtil.assertPublishesMetrics(client, operatorNamespace, METRICS_SERVICE_NAME, METRICS_SERVICE_PORT);

        // scale down the legacy operator
        // kubectl scale deployment <release-name>-hivemq-operator-operator --replicas=0 -n <namespace>
        final var legacyOperatorDeploymentName = "%s-hivemq-operator-operator".formatted(LEGACY_RELEASE_NAME);
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
        //helmChartK3sContainer.uninstallRelease(LEGACY_RELEASE_NAME, operatorNamespace, "--cascade", "orphan");

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

        // if a legacy configuration for an extension is in place, we need to update the existing
        // ConfigMap by adding a new entry for `config.xml`.
        K8sUtil.updateConfigMap(client, operatorNamespace, "ese-legacy-config-map-update.yml");

        helmUpgradePlatform.withName(LEGACY_RELEASE_NAME)
                .withNamespace(operatorNamespace)
                .withValuesFile(VALUES_PATH.resolve("migration-platform-stateful-set-values.yaml"))
                .call();
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
        final var platformServiceLabels = mergeMaps(platformLabels, Map.of("hivemq/platform-service", "true"));
        final var clusterServiceLabels = mergeMaps(platformLabels, Map.of("hivemq/cluster-service", "true"));
        assertAnnotationsAndLabels("Migrated MQTT Service",
                client.services().inNamespace(operatorNamespace).withName(MQTT_SERVICE_NAME).get().getMetadata(),
                mqttAnnotations,
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated Control Center Service",
                client.services().inNamespace(operatorNamespace).withName(CC_SERVICE_NAME).get().getMetadata(),
                Map.of(),
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated Metrics Service",
                client.services().inNamespace(operatorNamespace).withName(METRICS_SERVICE_NAME).get().getMetadata(),
                metricsAnnotations,
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated REST API Service",
                client.services().inNamespace(operatorNamespace).withName(REST_API_SERVICE_NAME).get().getMetadata(),
                Map.of(),
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated Cluster Service",
                client.services().inNamespace(operatorNamespace).withName(CLUSTER_SERVICE_NAME).get().getMetadata(),
                Map.of(),
                clusterServiceLabels);
        assertAnnotationsAndLabels("Migrated StatefulSet",
                client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(LEGACY_RELEASE_NAME)
                        .get()
                        .getMetadata(),
                Map.of("operator.platform.hivemq.com/last-controller-revision-hash", ""),
                platformLabels);
        final var statefulSetMetadata = client.apps()
                .statefulSets()
                .inNamespace(operatorNamespace)
                .withName(LEGACY_RELEASE_NAME)
                .get()
                .getSpec()
                .getTemplate()
                .getMetadata();
        assertThat(statefulSetMetadata.getAnnotations()).containsOnlyKeys("kubernetes-resource-versions");
        assertAnnotationsAndLabels("Migrated StatefulSet Template",
                statefulSetMetadata,
                statefulSetMetadata.getAnnotations(),
                platformLabels);
        // assert Control Center login
        ControlCenterUtil.assertLogin(client,
                operatorNamespace,
                WEB_DRIVER_CONTAINER,
                CC_SERVICE_NAME,
                CC_SERVICE_PORT);
        // assert retained messages
        MqttUtil.assertRetainedMessages(client, operatorNamespace, topicList, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);
        // assert RestAPI service and authorization with ESE extension
        RestAPIUtil.assertAuth(client, operatorNamespace, REST_API_SERVICE_NAME, REST_API_SERVICE_PORT, false);
        // assert subscribe and publishes metrics
        MonitoringUtil.assertSubscribesMetrics(client, operatorNamespace, METRICS_SERVICE_NAME, METRICS_SERVICE_PORT);
    }

    private void assertAnnotationsAndLabels(
            final @NotNull String resourceName,
            final @NotNull ObjectMeta actualMetadata,
            final @NotNull Map<String, String> expectedAnnotations,
            final @NotNull Map<String, String> expectedLabels) {
        // ignore runtime annotations from JOSDK
        final var actualAnnotations = actualMetadata.getAnnotations()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().startsWith("javaoperatorsdk.io/"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // ignore runtime labels from Helm
        final var actualLabels = actualMetadata.getLabels()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().contains("jobLabel"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        final var description = """
                %s %%s
                  actual   annotations: %s
                  expected annotations: %s
                  actual   labels: %s
                  expected labels: %s""".formatted(resourceName,
                new TreeMap<>(actualAnnotations),
                new TreeMap<>(expectedAnnotations),
                new TreeMap<>(actualLabels),
                new TreeMap<>(expectedLabels));
        assertThat(actualAnnotations).as(description, "annotations").isEqualTo(expectedAnnotations);
        assertThat(actualLabels).as(description, "labels").isEqualTo(expectedLabels);
    }

    private static @NotNull Map<String, String> mergeMaps(
            final @NotNull Map<String, String> map1,
            final @NotNull Map<String, String> map2) {
        final var result = new HashMap<>(map1);
        result.putAll(map2);
        return result;
    }
}
