package com.hivemq.helmcharts.volumes;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HelmPvcRetentionPolicyIT extends AbstractHelmChartIT {

    private static final @NotNull String PVC_NAME_PREFIX = "hivemq-pvc-data-";
    private static final int REPLICAS = 1;

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "LATEST",
                             disabledReason = "requires K8s >= 1.27 where persistentVolumeClaimRetentionPolicy is honored")
    void platform_whenPvcRetentionPolicyAllDelete_thenAllPvcsCleanedUp() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/pvc-retention-policy-values.yaml");

        await().untilAsserted(() -> assertThat(listPlatformPvcs()).hasSize(REPLICAS));
        await().untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(platformReleaseName).get();
            assertThat(statefulSet).isNotNull();
            final var retention = statefulSet.getSpec().getPersistentVolumeClaimRetentionPolicy();
            assertThat(retention).isNotNull();
            assertThat(retention.getWhenScaled()).isEqualTo("Delete");
            assertThat(retention.getWhenDeleted()).isEqualTo("Delete");
            assertThat(listPlatformPvcs()).hasSize(REPLICAS);
        });

        // trigger a rolling restart by updating the platform ConfigMap
        K8sUtil.updateConfigMap(client,
                platformNamespace,
                "hivemq-config-map-update.yml",
                "hivemq-configuration-" + platformReleaseName);
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, platformReleaseName);

        await().atMost(2, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listPlatformPvcs()).as(
                        "surge PVC must be deleted by K8s on scale-down (whenScaled=Delete)").hasSize(REPLICAS));

        helmChartContainer.uninstallRelease(platformReleaseName, platformNamespace, false);
        // wait for the StatefulSet to be fully gone
        await().atMost(1, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> client.apps()
                        .statefulSets()
                        .inNamespace(platformNamespace)
                        .withName(platformReleaseName)
                        .get() == null);

        // data PVCs are cleaned up by K8s after StatefulSet deletion (whenDeleted: Delete).
        await().atMost(2, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listPlatformPvcs()).as(
                        "data PVCs must be deleted after StatefulSet deletion when whenDeleted is Delete").isEmpty());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "MINIMUM",
                             disabledReason = "requires K8s < 1.27 where the alpha field is dropped by the API server")
    void platform_whenWhenScaledDelete_onAlphaK8s_thenSurgePvcRemains() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/pvc-retention-policy-values.yaml");
        await().untilAsserted(() -> assertThat(listPlatformPvcs()).hasSize(REPLICAS));

        // trigger a rolling restart by updating the platform ConfigMap (same pattern as the LATEST test above)
        K8sUtil.updateConfigMap(client,
                platformNamespace,
                "hivemq-config-map-update.yml",
                "hivemq-configuration-" + platformReleaseName);
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, platformReleaseName);

        // during 30 seconds, ensure PVC for surge pod was not deleted
        await().during(30, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listPlatformPvcs()).as(
                                "surge PVC should remain on K8s < 1.27 because StatefulSetAutoDeletePVC feature gate is off")
                        .hasSize(REPLICAS + 1));

        helmChartContainer.uninstallRelease(platformReleaseName, platformNamespace, false);
        // wait for the StatefulSet to be fully gone
        await().atMost(1, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> client.apps()
                        .statefulSets()
                        .inNamespace(platformNamespace)
                        .withName(platformReleaseName)
                        .get() == null);

        // during 15 seconds, ensure all PVCs remain
        await().during(15, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listPlatformPvcs()).as(
                                "all PVCs must remain after helm uninstall on alpha K8s (retention policy was dropped, K8s defaults to Retain)")
                        .hasSize(REPLICAS + 1));
    }

    private @NotNull List<PersistentVolumeClaim> listPlatformPvcs() {
        return client.persistentVolumeClaims()
                .inNamespace(platformNamespace)
                .list()
                .getItems()
                .stream()
                .filter(pvc -> pvc.getMetadata().getName().startsWith(PVC_NAME_PREFIX))
                .toList();
    }
}
