package com.hivemq.helmcharts.containers;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_MINUTES;

class HelmInitContainerConsulTemplateIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    @EnabledIfSystemProperty(named = "k3s.version.type",
                             matches = "LATEST",
                             disabledReason = "spec.templates.spec.initContainers[].restartPolicy was added in K8s 1.28")
    void whenAdditionalInitContainer_withConsulTemplate_thenLicenseUpdated() throws Exception {
        K8sUtil.createConfigMap(client, platformNamespace, "consul-template-config-map.yml");
        installPlatformChartAndWaitToBeRunning("/files/additional-init-containers-values.yaml");

        await().atMost(TWO_MINUTES).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var hivemqContainer = K8sUtil.getHiveMQContainer(statefulSet.getSpec());
            assertThat(hivemqContainer.getVolumeMounts().stream()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("license-volume") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/license"));
            final var additionalInitContainer =
                    K8sUtil.getInitContainer(statefulSet.getSpec(), "consul-template-init-container");
            assertThat(additionalInitContainer.getVolumeMounts().stream()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("license-volume") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/license"));

            final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
            assertThat(volumes).isNotNull();
            assertThat(volumes.stream()) //
                    .anyMatch(volume -> volume.getName().equals("license-volume") && volume.getEmptyDir() != null);
        });
        waitForPlatformLog(".*License file license.lic is corrupt.");
    }
}
