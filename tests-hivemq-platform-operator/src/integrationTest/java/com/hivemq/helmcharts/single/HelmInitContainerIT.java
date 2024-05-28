package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Containers")
@SuppressWarnings("DuplicatedCode")
class HelmInitContainerIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withOverrideInitContainer_hivemqRunningWithVolumeMounts() throws Exception {
        final String additionalVolumeFile = "/files/init-container-additional-volumes-test-values.yaml";
        final String additionalInitContainerFile = "/files/init-containers-spec.yaml";

        final String mountName = "init-container-volume";

        installPlatformChartAndWaitToBeRunning("--set-file",
                "config.overrideInitContainers=" + additionalInitContainerFile,
                "-f",
                additionalVolumeFile);

        await().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var template = statefulSet.getSpec().getTemplate();
            assertThat(template.getSpec().getVolumes()).isNotEmpty().map(Volume::getName).contains(mountName);
            final var containerVolumeMounts = template.getSpec().getContainers().getFirst().getVolumeMounts();
            assertThat(containerVolumeMounts).isNotEmpty().map(VolumeMount::getName).contains(mountName);

            await().untilAsserted(() -> assertThat(client.pods()
                    .inNamespace(platformNamespace)
                    .withName(PLATFORM_RELEASE_NAME + "-0")
                    .get()) //
                    .isNotNull() //
                    .satisfies(this::assertThatFileContains));
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenAdditionalInitContainer_withConsulTemplate_thenLicenseUpdated() throws Exception {
        K8sUtil.createConfigMap(client,
                platformNamespace,
                "consul-template-config-map.yml");
        installPlatformChartAndWaitToBeRunning("/files/additional-init-containers-test-values.yaml");

        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var hivemqContainer = K8sUtil.getHiveMQContainer(statefulSet.getSpec());
            assertThat(hivemqContainer.getVolumeMounts().stream()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("license-volume") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/license"));
            final var additionalInitContainer = K8sUtil.getInitContainer(statefulSet.getSpec(), "consul-template-init-container");
            assertThat(additionalInitContainer.getVolumeMounts().stream()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("license-volume") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/license"));

            final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
            assertThat(volumes).isNotNull();
            assertThat(volumes.stream()) //
                    .anyMatch(volume -> volume.getName().equals("license-volume") &&
                            volume.getEmptyDir() != null);
        });
        waitForPlatformLog(".*License file license.lic is corrupt.");
    }

    private void assertThatFileContains(final @NotNull Pod pod) {
        final String initContainerTextFile = "/init-container-test/init-container-test.txt";
        final String expectedContent = "test init container";

        try (InputStream in = client.pods()
                .inNamespace(platformNamespace)
                .withName(pod.getMetadata().getName())
                .file(initContainerTextFile)
                .read()) {
            final String foundContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(foundContent).isEqualTo(expectedContent);
        } catch (final IOException e) {
            throw new AssertionError("Could not read test file of init container from pod", e);
        }
    }
}
