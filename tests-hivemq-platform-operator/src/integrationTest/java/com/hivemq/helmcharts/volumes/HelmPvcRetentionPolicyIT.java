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
    private static final int REPLICAS = 2;

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "LATEST",
                             disabledReason = "persistentVolumeClaimRetentionPolicy.whenScaled is honored from K8s 1.27 (beta default-on); skipped on MINIMUM lane")
    void platform_whenWhenScaledDelete_andSurgeRollingRestart_thenSurgePvcIsDeleted() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/pvc-retention-policy-values.yaml");

        // sanity: STS exposes the retention policy and the initial PVC count matches REPLICAS (=2)
        await().untilAsserted(() -> {
            final var statefulSet = client.apps()
                    .statefulSets()
                    .inNamespace(platformNamespace)
                    .withName(platformReleaseName)
                    .get();
            assertThat(statefulSet).isNotNull();
            final var retention = statefulSet.getSpec().getPersistentVolumeClaimRetentionPolicy();
            assertThat(retention).isNotNull();
            assertThat(retention.getWhenScaled()).isEqualTo("Delete");
            assertThat(retention.getWhenDeleted()).isEqualTo("Retain");
            assertThat(listPlatformPvcs()).hasSize(REPLICAS);
        });

        // trigger a rolling restart by updating the platform ConfigMap (same pattern as HelmVolumeClaimTemplatesIT)
        K8sUtil.updateConfigMap(client,
                platformNamespace,
                "hivemq-config-map-update.yml",
                "hivemq-configuration-" + platformReleaseName);

        // the STS briefly scales to replicas+1, creating a surge PVC; we don't assert that here because the
        // window is too short to catch reliably on K3s-in-Docker and the post-restart assertion already pins
        // the contract we care about

        // wait for the platform to settle back to Running after the rolling restart
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, platformReleaseName);

        // final assertion: the surge PVC was deleted by kube-controller-manager
        await().atMost(2, TimeUnit.MINUTES)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listPlatformPvcs()).as(
                                "surge PVC must be deleted by K8s on scale-down when whenScaled=Delete")
                        .hasSize(REPLICAS));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "MINIMUM",
                             disabledReason = "exercises the documented limitation: persistentVolumeClaimRetentionPolicy is alpha and gated on K8s 1.24-1.26; field is accepted but ignored. Skipped on LATEST.")
    void platform_whenWhenScaledDelete_onAlphaK8s_thenSurgePvcRemains() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/pvc-retention-policy-values.yaml");

        // sanity: chart-side rendering works on the minimum K8s version too; the STS spec carries the policy
        // even though the controller will ignore it because the StatefulSetAutoDeletePVC feature gate is off
        await().untilAsserted(() -> {
            final var statefulSet = client.apps()
                    .statefulSets()
                    .inNamespace(platformNamespace)
                    .withName(platformReleaseName)
                    .get();
            assertThat(statefulSet).isNotNull();
            final var retention = statefulSet.getSpec().getPersistentVolumeClaimRetentionPolicy();
            assertThat(retention).isNotNull();
            assertThat(retention.getWhenScaled()).isEqualTo("Delete");
            assertThat(retention.getWhenDeleted()).isEqualTo("Retain");
            assertThat(listPlatformPvcs()).hasSize(REPLICAS);
        });

        // trigger a rolling restart by updating the platform ConfigMap (same pattern as the LATEST test above)
        K8sUtil.updateConfigMap(client,
                platformNamespace,
                "hivemq-config-map-update.yml",
                "hivemq-configuration-" + platformReleaseName);

        // wait for the platform to settle back to Running after the rolling restart
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, platformReleaseName);

        // final assertion: the surge PVC was NOT deleted because StatefulSetAutoDeletePVC is off on K8s 1.24-1.26;
        // the .during(30s) ensures the count is stable at REPLICAS + 1 (not just briefly) which proves the
        // controller is not going to delete it later
        await().during(30, TimeUnit.SECONDS)
                .atMost(2, TimeUnit.MINUTES)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(listPlatformPvcs()).as(
                                "surge PVC should remain on K8s < 1.27 because StatefulSetAutoDeletePVC feature gate is off")
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
