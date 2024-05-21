package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Platform")
class HelmSidecarContainerIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platform_whenSidecarContainer_withConsulTemplateThenLicenseUpdated() throws Exception {
        K8sUtil.createConfigMap(client,
                platformNamespace,
                "consul-template-config-map.yml");
        installPlatformChartAndWaitToBeRunning("/files/sidecar-containers-test-values.yaml");
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var hivemqContainer = K8sUtil.getHiveMQContainer(statefulSet.getSpec());
            assertThat(hivemqContainer.getVolumeMounts().stream()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("license-volume") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/license"));
            final var sidecarContainer = K8sUtil.getContainer(statefulSet.getSpec(), "consul-template");
            assertThat(sidecarContainer.getVolumeMounts().stream()) //
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
}
