package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Licenses")
@Tag("Secrets")
class HelmLicenseSecretIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "license.create=true",
                "--set-file",
                "license.overrideLicense=/files/mock-license.lic");
        assertLicense("hivemq-license-test-hivemq-platform");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingSecret_statefulSetWithLicenseSecretMounted() throws Exception {
        K8sUtil.createSecret(client,
                platformNamespace,
                "test-license",
                Map.of("license.lic",
                        Base64.getEncoder().encodeToString("license data".getBytes(StandardCharsets.UTF_8))));
        installPlatformChartAndWaitToBeRunning("--set", "nodes.replicaCount=1", "--set", "license.name=test-license");
        assertLicense("test-license");
    }

    @SuppressWarnings("SameParameterValue")
    private void assertLicense(final @NotNull String licenseSecretName) {
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
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
