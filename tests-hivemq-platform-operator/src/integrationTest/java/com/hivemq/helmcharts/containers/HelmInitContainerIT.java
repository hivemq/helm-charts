package com.hivemq.helmcharts.containers;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HelmInitContainerIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withOverrideInitContainer_hivemqRunningWithVolumeMounts() throws Exception {
        final var additionalVolumeFile = "/files/init-container-additional-volumes-values.yaml";
        final var additionalInitContainerFile = "/files/init-containers-spec.yaml";

        final var mountName = "init-container-volume";

        installPlatformChartAndWaitToBeRunning("--set-file",
                "config.overrideInitContainers=" + additionalInitContainerFile,
                "-f",
                additionalVolumeFile);

        await().untilAsserted(() -> {
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

    private void assertThatFileContains(final @NotNull Pod pod) {
        final var initContainerTextFile = "/init-container-test/init-container-test.txt";
        final var expectedContent = "test init container";
        try (final var inputStream = client.pods()
                .inNamespace(platformNamespace)
                .withName(pod.getMetadata().getName())
                .file(initContainerTextFile)
                .read()) {
            final var foundContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(foundContent).isEqualTo(expectedContent);
        } catch (final IOException e) {
            throw new AssertionError("Could not read test file of init container from pod", e);
        }
    }
}
