package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("CustomConfig")
@Tag("Volumes")
class HelmInitContainerIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withAdditionalInitContainer_hivemqRunningWithVolumeMounts() throws Exception {
        final String additionalVolumeFile = "/files/init-container-additional-volumes-test-values.yaml";
        final String additionalInitContainerFile = "/files/init-containers-spec.yaml";

        final String mountName = "init-container-volume";

        installChartsAndWaitForPlatformRunning(additionalVolumeFile,
                "config.overrideInitContainers=" + additionalInitContainerFile);

        await().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(namespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var template = statefulSet.getSpec().getTemplate();
            assertThat(template.getSpec().getVolumes()).isNotEmpty().map(Volume::getName).contains(mountName);
            final var containerVolumeMounts = template.getSpec().getContainers().getFirst().getVolumeMounts();
            assertThat(containerVolumeMounts).isNotEmpty().map(VolumeMount::getName).contains(mountName);

            await().untilAsserted(() -> assertThat(client.pods()
                    .inNamespace(namespace)
                    .withName("test-hivemq-platform-0")
                    .get()) //
                    .isNotNull() //
                    .satisfies(this::assertThatFileContains));
        });
    }

    private void assertThatFileContains(final @NotNull Pod pod) {
        final String initContainerTextFile = "/init-container-test/init-container-test.txt";
        final String expectedContent = "test init container";

        try (InputStream in = client.pods()
                .inNamespace(namespace)
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
