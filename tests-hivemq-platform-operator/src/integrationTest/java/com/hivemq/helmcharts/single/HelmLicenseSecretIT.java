package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

    private static final @NotNull String NAMESPACE = K8sUtil.getNamespaceName(HelmLicenseSecretIT.class);

    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseSetup() throws Exception {
        HELM_CHART_CONTAINER.start();
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME);
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseTearDown() {
        HELM_CHART_CONTAINER.stop();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setup() {
        HELM_CHART_CONTAINER.createNamespace(NAMESPACE);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, NAMESPACE, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "license.create=true",
                "--set-file",
                "license.overrideLicense=/files/mock-license.lic");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);
        assertLicense(customResourceName, "hivemq-license-test-hivemq-platform");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingSecret_statefulSetWithLicenseSecretMounted() throws Exception {
        final var customResourceName = "test-hivemq-platform";
        K8sUtil.createSecret(client,
                NAMESPACE,
                "test-license",
                Map.of("license.lic",
                        Base64.getEncoder().encodeToString("license data".getBytes(StandardCharsets.UTF_8))));
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--namespace",
                NAMESPACE,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "license.name=test-license");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, NAMESPACE, customResourceName);
        assertLicense(customResourceName, "test-license");
    }

    @SuppressWarnings("SameParameterValue")
    private void assertLicense(
            final @NotNull String customResourceName, final @NotNull String licenseSecretName) {
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(5)).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(NAMESPACE).withName(customResourceName).get();
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
