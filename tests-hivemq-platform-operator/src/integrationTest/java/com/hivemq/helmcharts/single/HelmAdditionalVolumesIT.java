package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
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
        client.persistentVolumeClaims().inNamespace(platformNamespace).resource(pvc).create();

        K8sUtil.createConfigMap(client, platformNamespace, "test-configmap-volume", Map.of("test.xml", "test-content"));
        K8sUtil.createSecret(client,
                platformNamespace,
                "test-secret-volume",
                Map.of("test.xml",
                        Base64.getEncoder().encodeToString("test-content".getBytes(StandardCharsets.UTF_8))));

        installPlatformChartAndWaitToBeRunning("/files/additional-volumes-values.yaml");

        await().untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
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
