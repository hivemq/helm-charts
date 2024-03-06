package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
class HelmLicenseSecretIT {

    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void setup() throws Exception {
        HELM_CHART_CONTAINER.start();
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    public static void shutdown() {
        HELM_CHART_CONTAINER.stop();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "license-file-content";
        HELM_CHART_CONTAINER.createNamespace(namespace);

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                namespace,
                "--set-file",
                "license.fileContent=/files/mock-license.lic");

        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(namespace).withName(customResourceName).get();
            assertThat(statefulSet).isNotNull();
            final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
            assertThat(volumes).isNotNull();
            final var licenseVolume = statefulSet.getSpec()
                    .getTemplate()
                    .getSpec()
                    .getVolumes()
                    .stream()
                    .filter(v -> v.getName().equals("licenses"))
                    .findFirst();
            assertThat(licenseVolume).isNotNull();
        });
        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingSecret_statefulSetWithLicenseSecretMounted() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        final var namespace = "existing-license-secret";
        HELM_CHART_CONTAINER.createNamespace(namespace);

        final var licenseSecret = new SecretBuilder().withNewMetadata()
                .withName("test-license")
                .endMetadata()
                .withData(Map.of("license.lic",
                        Base64.getEncoder().encodeToString("license data".getBytes(StandardCharsets.UTF_8))))
                .build();

        client.secrets().inNamespace(namespace).resource(licenseSecret).create();

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                namespace,
                "--set",
                "license.name=test-license");

        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(namespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var volumes = statefulSet.getSpec().getTemplate().getSpec().getVolumes();
            assertThat(volumes).isNotNull();
            final var licenseVolume = statefulSet.getSpec()
                    .getTemplate()
                    .getSpec()
                    .getVolumes()
                    .stream()
                    .filter(v -> v.getName().equals("licenses"))
                    .findFirst();
            assertThat(licenseVolume).isPresent()
                    .get()
                    .satisfies(licVolume -> assertThat(licVolume.getSecret()
                            .getSecretName()).isEqualTo("test-license"));
        });
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, customResourceName);

        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                namespace);
        HELM_CHART_CONTAINER.deleteNamespace(namespace);
    }
}
