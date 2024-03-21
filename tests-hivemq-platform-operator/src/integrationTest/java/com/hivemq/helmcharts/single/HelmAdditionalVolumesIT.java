package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("CustomConfig")
@Tag("Volumes")
class HelmAdditionalVolumesIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withAdditionalVolumes_hivemqRunningWithVolumeMounts() throws Exception {
        final var pvc = new PersistentVolumeClaimBuilder().withNewMetadata()
                .withName("test-persistent-volume-claim")
                .endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withStorageClassName("local-path")
                .withNewResources()
                .addToRequests("storage", new Quantity("1Gi"))
                .endResources()
                .endSpec()
                .build();
        client.persistentVolumeClaims().inNamespace(namespace).resource(pvc).create();

        final var testConfigMap = new ConfigMapBuilder().withNewMetadata()
                .withName("test-configmap-volume")
                .endMetadata()
                .withData(Map.of("test.xml", "test-content"))
                .build();
        client.configMaps().inNamespace(namespace).resource(testConfigMap).create();

        final var testSecret = new SecretBuilder().withNewMetadata()
                .withName("test-secret-volume")
                .endMetadata()
                .withData(Map.of("test.xml",
                        Base64.getEncoder().encodeToString("test-content".getBytes(StandardCharsets.UTF_8))))
                .build();
        client.secrets().inNamespace(namespace).resource(testSecret).create();

        installChartsAndWaitForPlatformRunning("/files/additional-volumes-test-values.yaml");

        await().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(namespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var template = statefulSet.getSpec().getTemplate();
            assertThat(template.getSpec().getVolumes()).isNotEmpty()
                    .map(Volume::getName)
                    .contains("test-mount-volume",
                            "test-secret-volume",
                            "test-persistent-volume-claim",
                            "test-empty-dir-volume");
            final var containerVolumeMounts = template.getSpec().getContainers().getFirst().getVolumeMounts();
            assertThat(containerVolumeMounts).isNotEmpty()
                    .map(VolumeMount::getName)
                    .contains("test-mount-volume",
                            "test-secret-volume",
                            "test-persistent-volume-claim",
                            "test-empty-dir-volume");
            assertThat(containerVolumeMounts).filteredOn(v -> v.getSubPath() != null)
                    .extracting(VolumeMount::getSubPath)
                    .contains("test.xml");
        });
    }
}
