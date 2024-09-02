package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.ControlCenterUtil;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MonitoringUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import com.hivemq.helmcharts.util.RestAPIUtil;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
        helmChartContainer.uninstallRelease(LEGACY_RELEASE_NAME, operatorNamespace, false);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void migrateToCurrentPlatformOperator() throws Exception {
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-legacy-config-map.yml");
        K8sUtil.createConfigMap(client, operatorNamespace, "ese-file-realm-config-map.yml");
        installLegacyOperatorChartAndWaitToBeRunning("/files/migration-legacy-stateful-set-values.yaml");

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

        //TODO: We cannot execute `helm uninstall my-release --cascade orphan` - See: https://github.com/helm/helm/issues/13279
        // we need to uninstall the legacy release with orphan option so the new platform Helm install takes over existing resources
        //helmChartContainer.uninstallRelease(LEGACY_RELEASE_NAME, operatorNamespace, "--cascade", "orphan");

        // as a workaround we need to delete the Helm secret installed
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
}
