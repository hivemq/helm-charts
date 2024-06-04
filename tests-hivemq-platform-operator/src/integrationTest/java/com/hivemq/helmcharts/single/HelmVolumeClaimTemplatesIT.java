package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Volumes")
class HelmVolumeClaimTemplatesIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platform_whenVolumeClaimTemplateIsConfigured_withAdditionalVolumes_thenPersistentVolumeClaimsCreated()
            throws Exception {

        installPlatformChartAndWaitToBeRunning("/files/volumes-claim-templates-test-values.yaml");

        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();

            // assert StatefulSet volumes
            final var templateSpec = statefulSet.getSpec().getTemplate().getSpec();
            assertVolumes(templateSpec.getVolumes(), "hivemq-pvc-data", "hivemq-pvc-data");

            // assert Container volume mounts
            assertVolumeMounts(templateSpec.getContainers().getFirst().getVolumeMounts(),
                    "hivemq-pvc-data",
                    "/opt/hivemq/data");

            // assert VolumeClaimTemplates
            final var volumeClaimTemplates = statefulSet.getSpec().getVolumeClaimTemplates();
            assertThat(volumeClaimTemplates).isNotEmpty().hasSize(1);
            assertPersistentVolumeClaim(volumeClaimTemplates.getFirst(), "hivemq-pvc-data");

            // assert Platform pods
            final var platformPod =
                    client.pods().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME + "-0").get();
            assertThat(platformPod).isNotNull();
            final var podSpec = platformPod.getSpec();
            assertVolumes(podSpec.getVolumes(),
                    "hivemq-pvc-data",
                    String.format("hivemq-pvc-data-%s-0", PLATFORM_RELEASE_NAME));
            assertVolumeMounts(podSpec.getContainers().getFirst().getVolumeMounts(),
                    "hivemq-pvc-data",
                    "/opt/hivemq/data");

            // assert PersistentVolumeClaims
            final var pvcName = String.format("hivemq-pvc-data-%s-0", PLATFORM_RELEASE_NAME);
            assertPersistentVolumeClaim(client.persistentVolumeClaims()
                    .inNamespace(platformNamespace)
                    .withName(pvcName)
                    .get(), pvcName);
        });
    }


    @SuppressWarnings("SameParameterValue")
    private static void assertVolumes(
            final @NotNull List<Volume> volumes,
            final @NotNull String expectedVolumeName,
            final @NotNull String expectedClaimName) {
        assertThat(volumes).isNotNull().isNotEmpty().anySatisfy(volume -> {
            assertThat(volume.getName()).isEqualTo(expectedVolumeName);
            assertThat(volume.getPersistentVolumeClaim()).isNotNull();
            assertThat(volume.getPersistentVolumeClaim().getClaimName()).isEqualTo(expectedClaimName);
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static void assertVolumeMounts(
            final @NotNull List<VolumeMount> volumeMounts,
            final @NotNull String expectedVolumeMountName,
            final @NotNull String expectedMountPath) {
        assertThat(volumeMounts).isNotNull().isNotEmpty().anySatisfy(volumeMount -> {
            assertThat(volumeMount.getName()).isEqualTo(expectedVolumeMountName);
            assertThat(volumeMount.getMountPath()).isEqualTo(expectedMountPath);
        });
    }

    private static void assertPersistentVolumeClaim(
            final @NotNull PersistentVolumeClaim pvc, final @NotNull String expectedPvcName) {
        assertThat(pvc).isNotNull();
        assertThat(pvc.getMetadata().getName()).isEqualTo(expectedPvcName);
        final var pvcSpec = pvc.getSpec();
        assertThat(pvcSpec).isNotNull();
        assertThat(pvcSpec.getAccessModes()).isNotEmpty().hasSize(1);
        assertThat(pvcSpec.getAccessModes().getFirst()).isEqualTo("ReadWriteOnce");
        assertThat(pvcSpec.getResources()).isNotNull();
        assertThat(pvcSpec.getResources().getRequests()).hasSize(1).containsEntry("storage", new Quantity("1Gi"));
        assertThat(pvcSpec.getVolumeMode()).isEqualTo("Filesystem");
    }
}
