package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.PodUtil;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Volumes")
class HelmVolumeClaimTemplatesIT extends AbstractHelmChartIT {

    private static final @NotNull String PVC_NAME = "hivemq-pvc-data";

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void platform_whenVolumeClaimTemplateIsConfigured_withAdditionalVolumes_thenPersistentVolumeClaimsCreated()
            throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/volumes-claim-templates-values.yaml");

        // assert StatefulSet
        await().untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();

            // assert StatefulSet volumes
            final var templateSpec = statefulSet.getSpec().getTemplate().getSpec();
            assertVolumes(templateSpec.getVolumes(), PVC_NAME, PVC_NAME);

            // assert Container volume mounts
            assertVolumeMounts(templateSpec.getContainers().getFirst().getVolumeMounts(), PVC_NAME, "/opt/hivemq/data");

            // assert VolumeClaimTemplates
            final var volumeClaimTemplates = statefulSet.getSpec().getVolumeClaimTemplates();
            assertThat(volumeClaimTemplates).isNotEmpty().hasSize(1);
            assertPersistentVolumeClaim(volumeClaimTemplates.getFirst(), PVC_NAME);
        });

        // create files for each pod in their corresponding PVCs
        final var labels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        client.pods().inNamespace(platformNamespace).withLabels(labels).list().getItems().forEach(pod -> {
            final var mountPath = pod.getSpec()
                    .getContainers()
                    .getFirst()
                    .getVolumeMounts()
                    .stream()
                    .filter(volumeMount -> PVC_NAME.equals(volumeMount.getName()))
                    .map(VolumeMount::getMountPath)
                    .findAny()
                    .orElseThrow(() -> new AssertionError(String.format("Could not find mount path %s", PVC_NAME)));
            final var podName = pod.getMetadata().getName();
            final var fileName = String.format("%s.txt", podName);
            final var filePath = Path.of(mountPath).resolve(fileName);
            final var fileContentCommand = String.format("echo -n '%s' >> %s", getFileContentForPod(podName), filePath);
            K8sUtil.executeInHiveMQPod(client, platformNamespace, pod, "touch", filePath.toString());
            K8sUtil.executeInHiveMQPod(client, platformNamespace, pod, "sh", "-c", fileContentCommand);
        });

        // Update ConfigMap to trigger a rolling restart to make sure the files are persisted in their corresponding PVCs
        K8sUtil.updateConfigMap(client, platformNamespace, "hivemq-config-map-update.yml");
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert Platform pods
        await().untilAsserted(() -> client.pods()
                .inNamespace(platformNamespace)
                .withLabels(labels)
                .list()
                .getItems()
                .forEach(pod -> {
                    assertThat(pod).isNotNull();
                    final var podSpec = pod.getSpec();
                    final var podName = pod.getMetadata().getName();
                    final var pvcNameForPod = String.format("%s-%s", PVC_NAME, podName);
                    assertVolumes(podSpec.getVolumes(), PVC_NAME, pvcNameForPod);
                    assertVolumeMounts(podSpec.getContainers().getFirst().getVolumeMounts(),
                            PVC_NAME,
                            "/opt/hivemq/data");

                    // assert PersistentVolumeClaims
                    assertPersistentVolumeClaim(client.persistentVolumeClaims()
                            .inNamespace(platformNamespace)
                            .withName(pvcNameForPod)
                            .get(), pvcNameForPod);

                    final var mountPath = pod.getSpec()
                            .getContainers()
                            .getFirst()
                            .getVolumeMounts()
                            .stream()
                            .filter(volumeMount -> PVC_NAME.equals(volumeMount.getName()))
                            .map(VolumeMount::getMountPath)
                            .findAny()
                            .orElseThrow(() -> new AssertionError(String.format("Could not find mount path %s",
                                    PVC_NAME)));
                    final var fileName = String.format("%s.txt", podName);
                    final var filePath = Path.of(mountPath).resolve(fileName);
                    assertFileInPersistentVolumeClaims(filePath, getFileContentForPod(podName), podName);
                }));
    }

    private void assertFileInPersistentVolumeClaims(
            final @NotNull Path filePath,
            final @NotNull String fileContent,
            final @NotNull String podName) {
        final var execResult =
                PodUtil.execute(client, platformNamespace, podName, "hivemq", "cat", filePath.toString());
        try {
            assertThat(execResult.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(execResult.getError()).as("stderr: %s", execResult.getError()).isNull();
            assertThat(execResult.exitCode()).isNotNull().isEqualTo(0);
            assertThat(execResult.getOutput()).as("stdout: %s", execResult.getOutput()).isEqualTo(fileContent);
        } catch (final Exception e) {
            throw new AssertionError(String.format("Could not get file %s in Pod '%s'", filePath, podName), e);
        } finally {
            execResult.close();
        }
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

    private static @NotNull String getFileContentForPod(final @NotNull String podName) {
        return String.format("Persisted content for pod %s", podName);
    }
}
