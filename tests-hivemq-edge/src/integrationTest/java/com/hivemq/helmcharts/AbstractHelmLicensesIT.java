package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_MINUTES;

public class AbstractHelmLicensesIT extends AbstractHelmChartIT {

    protected void assertLicense(final @NotNull String licenseSecretName) {
        await().atMost(TWO_MINUTES).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            assertThat(K8sUtil.getHiveMQContainer(statefulSet.getSpec()).getVolumeMounts().stream()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("licenses") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/license"));

            final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
            assertThat(volumes).isNotNull();
            assertThat(volumes.stream()) //
                    .anyMatch(volume -> volume.getName().equals("licenses") &&
                            volume.getSecret().getSecretName().equals(licenseSecretName));
        });
    }
}
