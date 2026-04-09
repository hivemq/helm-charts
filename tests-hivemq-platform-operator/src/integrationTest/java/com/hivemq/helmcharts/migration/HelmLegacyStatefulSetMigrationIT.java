package com.hivemq.helmcharts.migration;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.testcontainer.WebDriverContainerExtension;
import com.hivemq.helmcharts.util.ControlCenterUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MonitoringUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import com.hivemq.helmcharts.util.RestAPIUtil;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
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
@Disabled("The latest k8s image doesn't run with JDK 21.0.10+, so this test breaks due to https://bugs.openjdk.org/browse/JDK-8349988")
class HelmLegacyStatefulSetMigrationIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT = 1883;
    private static final int CC_SERVICE_PORT = 8080;
    private static final int METRICS_SERVICE_PORT = 9399;
    private static final int REST_API_SERVICE_PORT = 8888;

    private final @NotNull String mqttServiceName = "hivemq-%s-mqtt".formatted(legacyReleaseName);
    private final @NotNull String ccServiceName = "hivemq-%s-cc".formatted(legacyReleaseName);
    private final @NotNull String metricsServiceName = "hivemq-%s-metrics".formatted(legacyReleaseName);
    private final @NotNull String restApiServiceName = "hivemq-%s-api".formatted(legacyReleaseName);
    private final @NotNull String clusterServiceName = "hivemq-%s-cluster".formatted(legacyReleaseName);

    @RegisterExtension
    private static final @NotNull WebDriverContainerExtension WEB_DRIVER_CONTAINER_EXTENSION =
            new WebDriverContainerExtension(network);

    private @NotNull BrowserWebDriverContainer webDriverContainer;

    @Override
    protected boolean uninstallPlatformChart() {
        return false;
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseSetUp() {
        webDriverContainer = WEB_DRIVER_CONTAINER_EXTENSION.getWebDriverContainer();
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        helmChartContainer.uninstallRelease(legacyReleaseName, operatorNamespace);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "LATEST",
                             disabledReason = "there are various PVC related bugfixes in K8s that probably cause issues with older versions")
    void migrateToCurrentPlatformOperator() throws Exception {
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-legacy-config-map.yml");
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-file-realm-config-map.yml");
        installLegacyOperatorChartAndWaitToBeRunning("/files/migration-legacy-stateful-set-values.yaml");

        // assert service annotations and labels
        final var mqttAnnotations = Map.of("service.spec.externalTrafficPolicy", "Local");
        final var metricsAnnotations = Map.of("prometheus.io/scrape", "true");
        final var legacyLabels = Map.of("app", "hivemq", "hivemq-cluster", legacyReleaseName);
        assertAnnotationsAndLabels("Legacy MQTT Service",
                client.services().inNamespace(operatorNamespace).withName(mqttServiceName).get().getMetadata(),
                mqttAnnotations,
                legacyLabels);
        assertAnnotationsAndLabels("Legacy Control Center Service",
                client.services().inNamespace(operatorNamespace).withName(ccServiceName).get().getMetadata(),
                Map.of(),
                legacyLabels);
        assertAnnotationsAndLabels("Legacy Metrics Service",
                client.services().inNamespace(operatorNamespace).withName(metricsServiceName).get().getMetadata(),
                metricsAnnotations,
                legacyLabels);
        assertAnnotationsAndLabels("Legacy REST API Service",
                client.services().inNamespace(operatorNamespace).withName(restApiServiceName).get().getMetadata(),
                Map.of(),
                legacyLabels);
        assertAnnotationsAndLabels("Legacy Cluster Service",
                client.services().inNamespace(operatorNamespace).withName(clusterServiceName).get().getMetadata(),
                Map.of(),
                legacyLabels);
        final var legacyStatefulSetMetadata = client.apps()
                .statefulSets()
                .inNamespace(operatorNamespace)
                .withName(legacyReleaseName)
                .get()
                .getMetadata();
        assertThat(legacyStatefulSetMetadata.getAnnotations()).containsOnlyKeys("hivemq.com/resource-spec",
                "kubernetes.io/change-cause");
        final var legacyChartVersion = helmChartContainer.getLegacyOperatorChart().getVersion();
        assertAnnotationsAndLabels("Legacy StatefulSet",
                legacyStatefulSetMetadata,
                legacyStatefulSetMetadata.getAnnotations(),
                mergeMaps(legacyLabels,
                        Map.of("app.kubernetes.io/instance",
                                legacyReleaseName,
                                "app.kubernetes.io/managed-by",
                                "Helm",
                                "app.kubernetes.io/name",
                                "hivemq-operator",
                                "app.kubernetes.io/version",
                                "4.47.1",
                                "helm.sh/chart",
                                "hivemq-operator-%s".formatted(legacyChartVersion),
                                "hivemq-cluster",
                                legacyReleaseName)));
        assertAnnotationsAndLabels("Legacy StatefulSet Template",
                client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(legacyReleaseName)
                        .get()
                        .getSpec()
                        .getTemplate()
                        .getMetadata(),
                Map.of(),
                mergeMaps(legacyLabels, Map.of("hivemq.com/node-offline", "false")));
        // assert Control Center login
        ControlCenterUtil.assertLogin(client, operatorNamespace, webDriverContainer, ccServiceName, CC_SERVICE_PORT);
        // publish retained messages
        final var topicList =
                MqttUtil.publishRetainedMessages(client, operatorNamespace, mqttServiceName, MQTT_SERVICE_PORT);
        // assert RestAPI service and authorization with ESE extension
        RestAPIUtil.assertAuth(client, operatorNamespace, restApiServiceName, REST_API_SERVICE_PORT, false);
        // assert publishes metrics
        MonitoringUtil.assertPublishesMetrics(client, operatorNamespace, metricsServiceName, METRICS_SERVICE_PORT);

        // scale down the legacy operator
        // kubectl scale deployment <release-name>-hivemq-operator-operator --replicas=0 -n <namespace>
        final var legacyOperatorDeploymentName = "%s-hivemq-operator-operator".formatted(legacyReleaseName);
        K8sUtil.scaleDeployment(client, operatorNamespace, legacyOperatorDeploymentName, 0);
        var legacyOperatorLabels = K8sUtil.getHiveMQLegacyOperatorLabels(legacyReleaseName);
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
        final var legacyPlatform = K8sUtil.getLegacyHiveMQPlatform(client, operatorNamespace, legacyReleaseName);
        legacyPlatform.withPropagationPolicy(DeletionPropagation.ORPHAN).withTimeoutInMillis(5000).delete();
        assertThat(K8sUtil.getLegacyHiveMQPlatform(client, operatorNamespace, legacyReleaseName).get()).isNull();

        // scale up the legacy operator
        // kubectl scale deployment <release-name>-hivemq-operator-operator --replicas=1 -n <namespace>
        K8sUtil.scaleDeployment(client, operatorNamespace, legacyOperatorDeploymentName, 1);
        K8sUtil.waitForLegacyOperatorPodStateRunning(client, operatorNamespace, legacyReleaseName);
        assertThat(client.pods()
                .inNamespace(operatorNamespace)
                .withLabels(legacyOperatorLabels)
                .list()
                .getItems()
                .size()).isEqualTo(1);

        // we need to uninstall the legacy release with orphan option so existing resources can be taken over by the platform Helm install
        // TODO: We cannot execute `helm uninstall my-release --cascade orphan` because this will delete resources
        //  without an owner reference, that are still needed - See: https://github.com/helm/helm/issues/13279
        //helmChartContainer.uninstallRelease(legacyReleaseName, operatorNamespace, "--cascade", "orphan");

        // as a workaround we need to delete all the corresponding Helm versioned Secrets installed for each of the possible Helm releases
        // we may have. These will be named with a format like "sh.helm.release.v1.<release-name>.v<release-version>" and we will delete them
        // based on the labels instead as follows:
        // kubectl delete secret -l owner=helm,name=<release-name> -n <namespace>
        final var helmSecretLabels = Map.of("owner", "helm", "name", legacyReleaseName);
        client.secrets().inNamespace(operatorNamespace).withLabels(helmSecretLabels).withTimeoutInMillis(5000).delete();
        assertThat(client.secrets()
                .inNamespace(operatorNamespace)
                .withLabels(helmSecretLabels)
                .list()
                .getItems()).isEmpty();

        // if a legacy configuration for an extension is in place, we need to update the existing
        // ConfigMap by adding a new entry for `config.xml`.
        K8sUtil.updateConfigMap(client, operatorNamespace, "ese-legacy-config-map-update.yml");

        helmChartContainer.installPlatformChart(legacyReleaseName,
                "-f",
                "/files/migration-platform-stateful-set-values.yaml",
                "--namespace",
                operatorNamespace);

        K8sUtil.waitForHiveMQPlatformState(client, operatorNamespace, legacyReleaseName, "STATEFULSET_MIGRATION");
        K8sUtil.waitForHiveMQPlatformState(client, operatorNamespace, legacyReleaseName, "ROLLING_RESTART");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, operatorNamespace, legacyReleaseName);

        // assert service annotations and labels
        final var hiveMQVersion = System.getProperty("hivemq.tag", "latest");
        final var platformLabels = Map.of("app.kubernetes.io/instance",
                legacyReleaseName,
                "app.kubernetes.io/managed-by",
                "Helm",
                "app.kubernetes.io/name",
                "hivemq-platform",
                "app.kubernetes.io/version",
                hiveMQVersion,
                "hivemq-platform",
                legacyReleaseName);
        final var platformServiceLabels = mergeMaps(platformLabels, Map.of("hivemq/platform-service", "true"));
        final var clusterServiceLabels = mergeMaps(platformLabels, Map.of("hivemq/cluster-service", "true"));
        assertAnnotationsAndLabels("Migrated MQTT Service",
                client.services().inNamespace(operatorNamespace).withName(mqttServiceName).get().getMetadata(),
                mqttAnnotations,
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated Control Center Service",
                client.services().inNamespace(operatorNamespace).withName(ccServiceName).get().getMetadata(),
                Map.of(),
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated Metrics Service",
                client.services().inNamespace(operatorNamespace).withName(metricsServiceName).get().getMetadata(),
                metricsAnnotations,
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated REST API Service",
                client.services().inNamespace(operatorNamespace).withName(restApiServiceName).get().getMetadata(),
                Map.of(),
                platformServiceLabels);
        assertAnnotationsAndLabels("Migrated Cluster Service",
                client.services().inNamespace(operatorNamespace).withName(clusterServiceName).get().getMetadata(),
                Map.of(),
                clusterServiceLabels);
        assertAnnotationsAndLabels("Migrated StatefulSet",
                client.apps()
                        .statefulSets()
                        .inNamespace(operatorNamespace)
                        .withName(legacyReleaseName)
                        .get()
                        .getMetadata(),
                Map.of("operator.platform.hivemq.com/last-controller-revision-hash", ""),
                platformLabels);
        final var statefulSetMetadata = client.apps()
                .statefulSets()
                .inNamespace(operatorNamespace)
                .withName(legacyReleaseName)
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
        ControlCenterUtil.assertLogin(client, operatorNamespace, webDriverContainer, ccServiceName, CC_SERVICE_PORT);
        // assert retained messages
        MqttUtil.assertRetainedMessages(client, operatorNamespace, topicList, mqttServiceName, MQTT_SERVICE_PORT);
        // assert RestAPI service and authorization with ESE extension
        RestAPIUtil.assertAuth(client, operatorNamespace, restApiServiceName, REST_API_SERVICE_PORT, false);
        // assert subscribe and publishes metrics
        MonitoringUtil.assertSubscribesMetrics(client, operatorNamespace, metricsServiceName, METRICS_SERVICE_PORT);
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
