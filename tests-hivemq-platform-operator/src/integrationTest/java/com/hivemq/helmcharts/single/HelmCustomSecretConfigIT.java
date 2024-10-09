package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

@Tag("CustomConfig")
@Tag("CustomSecretConfig")
class HelmCustomSecretConfigIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withSecretConfig_hivemqRunning() throws Exception {
        final var secretName = "hivemq-configuration-" + PLATFORM_RELEASE_NAME;
        installPlatformChartAndWaitToBeRunning("--set", "config.createAs=Secret");
        assertSecretConfigMounted(secretName);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingSecret_hivemqRunning() throws Exception {
        final var secret = K8sUtil.createSecret(client,
                platformNamespace,
                "hivemq-configuration",
                readResourceFile("hivemq-config.xml"));
        final var secretName = secret.getMetadata().getName();

        installPlatformChartAndWaitToBeRunning("--set",
                "config.create=false",
                "--set",
                "config.name=" + secretName,
                "--set",
                "config.createAs=Secret");
        assertSecretConfigMounted(secretName);
    }

    private void assertSecretConfigMounted(final @NotNull String secretName) {
        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var hivemqCustomResource =
                    K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME).get();
            assertThat(hivemqCustomResource.getAdditionalProperties().get("spec")).isNotNull()
                    .asString()
                    .containsIgnoringCase("secretName=" + secretName);

            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            assertThat(K8sUtil.getHiveMQContainer(statefulSet.getSpec()).getVolumeMounts()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("broker-configuration") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/conf-k8s/"));

            assertThat(statefulSet.getSpec().getTemplate().getSpec().getVolumes()).isNotNull() //
                    .anyMatch(volume -> volume.getName().equals("broker-configuration") &&
                            volume.getSecret().getSecretName().equals(secretName));
        });
    }
}
