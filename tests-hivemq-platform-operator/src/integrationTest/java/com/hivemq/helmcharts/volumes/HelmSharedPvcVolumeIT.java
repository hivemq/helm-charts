package com.hivemq.helmcharts.volumes;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.PodUtil;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class HelmSharedPvcVolumeIT extends AbstractHelmChartIT {

    private static final @NotNull String PVC_NAME = "shared-pvc";
    private static final @NotNull String MOUNT_PATH = "/opt/hivemq/pvc";

    private static final @NotNull Map<String, String> ENV_VAR_TO_SUBFOLDER = Map.of( //
            "HIVEMQ_DATA_FOLDER", "data", //
            "HIVEMQ_LOG_FOLDER", "log", //
            "HIVEMQ_HEAPDUMP_FOLDER", "dump", //
            "HIVEMQ_BACKUP_FOLDER", "backup", //
            "HIVEMQ_AUDIT_FOLDER", "audit");

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "LATEST",
                             disabledReason = "there are various PVC related bugfixes in K8s that probably cause issues with older versions")
    void withSharedPvcVolume_hivemqRunningWithExpectedFolderStructureAndIsolatedData() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/shared-pvc-volumes-values.yaml");

        // verify StatefulSet spec: volume, mount, env vars, and volumeClaimTemplates
        final var statefulSet =
                client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
        assertThat(statefulSet).isNotNull();
        final var template = statefulSet.getSpec().getTemplate();

        assertThat(template.getSpec().getVolumes()).isNotEmpty().anySatisfy(volume -> {
            assertThat(volume.getName()).isEqualTo(PVC_NAME);
            assertThat(volume.getPersistentVolumeClaim()).isNotNull();
            assertThat(volume.getPersistentVolumeClaim().getClaimName()).isEqualTo(PVC_NAME);
        });

        final var container = template.getSpec().getContainers().getFirst();
        assertThat(container.getVolumeMounts()).isNotEmpty().anySatisfy(volumeMount -> {
            assertThat(volumeMount.getName()).isEqualTo(PVC_NAME);
            assertThat(volumeMount.getMountPath()).isEqualTo(MOUNT_PATH);
        });

        assertThat(container.getEnv()) //
                .extracting(EnvVar::getName) //
                .containsAll(ENV_VAR_TO_SUBFOLDER.keySet());
        ENV_VAR_TO_SUBFOLDER.forEach((envName, subfolder) -> //
                assertThat(container.getEnv()) //
                        .filteredOn(e -> envName.equals(e.getName())) //
                        .extracting(EnvVar::getValue) //
                        .containsExactly(MOUNT_PATH + "/" + subfolder));

        // assert volumeClaimTemplates
        final var volumeClaimTemplates = statefulSet.getSpec().getVolumeClaimTemplates();
        assertThat(volumeClaimTemplates).isNotEmpty().hasSize(1);
        final var vct = volumeClaimTemplates.getFirst();
        assertThat(vct.getMetadata().getName()).isEqualTo(PVC_NAME);
        assertThat(vct.getSpec().getAccessModes()).containsExactly("ReadWriteOnce");
        assertThat(vct.getSpec().getResources().getRequests()).containsEntry("storage", new Quantity("1Gi"));

        // get the running pods
        final var labels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        final var pods = client.pods().inNamespace(platformNamespace).withLabels(labels).list().getItems();
        assertThat(pods).hasSize(2);

        // verify PVCs are created
        for (final var pod : pods) {
            final var podName = pod.getMetadata().getName();
            final var pvcNameForPod = "%s-%s".formatted(PVC_NAME, podName);
            final var pvc =
                    client.persistentVolumeClaims().inNamespace(platformNamespace).withName(pvcNameForPod).get();
            assertThat(pvc).as("PVC '%s' should exist for pod '%s'", pvcNameForPod, podName).isNotNull();
        }

        // create subdirectories and write distinct content into each subfolder per pod
        for (final var pod : pods) {
            final var podName = pod.getMetadata().getName();
            for (final var subfolder : ENV_VAR_TO_SUBFOLDER.values()) {
                final var dir = Path.of(MOUNT_PATH, subfolder);
                K8sUtil.executeInHiveMQPod(client, platformNamespace, pod, "mkdir", "-p", dir.toString());
                K8sUtil.executeInHiveMQPod(client,
                        platformNamespace,
                        pod,
                        "sh",
                        "-c",
                        "echo -n '%s-%s-content' > %s/test-%s.txt".formatted(podName, subfolder, dir, subfolder));
            }
        }

        // verify each file exists only in its own subfolder per pod
        for (final var pod : pods) {
            assertSubfolderIsolation(pod);
        }

        // trigger a rolling restart and verify files persist
        K8sUtil.updateConfigMap(client, platformNamespace, "hivemq-config-map-update.yml");
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // after restart, verify files are still present and isolated in all pods
        final var restartedPods = client.pods()
                .inNamespace(platformNamespace)
                .withLabels(labels)
                .list()
                .getItems()
                .stream()
                .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .toList();
        assertThat(restartedPods).hasSize(2);

        for (final var pod : restartedPods) {
            assertSubfolderIsolation(pod);
        }
    }

    private void assertSubfolderIsolation(final @NotNull Pod pod) {
        final var podName = pod.getMetadata().getName();
        for (final var subfolder : ENV_VAR_TO_SUBFOLDER.values()) {
            final var dir = Path.of(MOUNT_PATH, subfolder);
            final var testFile = "test-%s.txt".formatted(subfolder);
            assertFileContent(pod, dir.resolve(testFile), podName + "-" + subfolder + "-content");

            // verify the test file does not exist in any other subfolder
            for (final var otherSubfolder : ENV_VAR_TO_SUBFOLDER.values()) {
                if (!otherSubfolder.equals(subfolder)) {
                    assertFileDoesNotExist(pod, Path.of(MOUNT_PATH, otherSubfolder, testFile));
                }
            }
        }
    }

    private void assertFileContent(
            final @NotNull Pod pod,
            final @NotNull Path filePath,
            final @NotNull String expectedContent) {
        final var podName = pod.getMetadata().getName();
        final var execResult =
                PodUtil.execute(client, platformNamespace, podName, "hivemq", "cat", filePath.toString());
        //noinspection CatchMayIgnoreException
        try {
            assertThat(execResult.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(execResult.getError()).as("stderr: %s", execResult.getError()).isNull();
            assertThat(execResult.exitCode()).isNotNull().isEqualTo(0);
            assertThat(execResult.getOutput()).as("File content of %s", filePath).isEqualTo(expectedContent);
        } catch (final Exception e) {
            fail("Could not read file '%s' in Pod '%s': %s", filePath, podName, e);
        } finally {
            execResult.close();
        }
    }

    private void assertFileDoesNotExist(final @NotNull Pod pod, final @NotNull Path filePath) {
        final var podName = pod.getMetadata().getName();
        final var execResult =
                PodUtil.execute(client, platformNamespace, podName, "hivemq", "test", "-f", filePath.toString());
        try {
            assertThat(execResult.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(execResult.exitCode()).as("File '%s' should not exist in Pod '%s'", filePath, podName)
                    .isNotNull()
                    .isNotEqualTo(0);
        } finally {
            execResult.close();
        }
    }
}
