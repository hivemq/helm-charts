package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_MINUTES;

@Tag("Licenses")
class HelmLicenseSecretIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBrokerLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var brokerLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file license.lic is corrupt.");
        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "license.create=true",
                "--set-file",
                "license.overrideLicense=/files/mock-license.lic");
        assertLicense("hivemq-license-test-hivemq-platform");
        await().until(brokerLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExtensionLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var tracingConfigMap =
                K8sUtil.createConfigMap(client, platformNamespace, "distributed-tracing-config-map.yml");
        assertThat(tracingConfigMap).isNotNull();

        final var extensionStartedFuture = waitForPlatformLog(
                ".*Extension \"HiveMQ Enterprise Distributed Tracing Extension\" version .* started successfully.");
        final var extensionLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file tracing.elic is corrupt.");

        installPlatformChartAndWaitToBeRunning("-f",
                "/files/distributed-tracing-extension-values.yaml",
                "--set",
                "license.create=true",
                "--set-file",
                "license.extensions.tracing.overrideLicense=/files/mock-extension-license.elic");
        assertLicense("hivemq-license-test-hivemq-platform");
        await().until(extensionStartedFuture::isDone);
        await().until(extensionLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withAdditionalBrokerLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var brokerLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file broker.lic is corrupt.");
        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "license.create=true",
                "--set-file",
                "license.additionalBroker.broker.overrideLicense=/files/mock-additional-license.lic");
        assertLicense("hivemq-license-test-hivemq-platform");
        await().until(brokerLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withDataHubLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var dataHubLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file dataHub1.plic is corrupt.");
        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "license.create=true",
                "--set-file",
                "license.dataHub.dataHub1.overrideLicense=/files/mock-data-hub-license.plic");
        assertLicense("hivemq-license-test-hivemq-platform");
        await().until(dataHubLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingBrokerLicenseSecret_statefulSetWithLicenseSecretMounted() throws Exception {
        final var brokerLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file license.lic is corrupt.");
        K8sUtil.createSecret(client,
                platformNamespace,
                "test-license",
                Map.of("license.lic",
                        Base64.getEncoder().encodeToString("license data".getBytes(StandardCharsets.UTF_8))));
        installPlatformChartAndWaitToBeRunning("--set", "nodes.replicaCount=1", "--set", "license.name=test-license");
        assertLicense("test-license");
        await().until(brokerLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingExtensionLicenseSecret_statefulSetWithLicenseSecretMounted() throws Exception {
        final var tracingConfigMap =
                K8sUtil.createConfigMap(client, platformNamespace, "distributed-tracing-config-map.yml");
        assertThat(tracingConfigMap).isNotNull();

        final var extensionStartedFuture = waitForPlatformLog(
                ".*Extension \"HiveMQ Enterprise Distributed Tracing Extension\" version .* started successfully.");
        final var extensionLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file tracing.elic is corrupt.");

        K8sUtil.createSecret(client,
                platformNamespace,
                "tracing-extension-secret-license",
                Map.of("tracing.elic",
                        Base64.getEncoder()
                                .encodeToString("tracing extension license data".getBytes(StandardCharsets.UTF_8))));
        installPlatformChartAndWaitToBeRunning("-f",
                "/files/distributed-tracing-extension-values.yaml",
                "--set",
                "license.name=tracing-extension-secret-license");
        assertLicense("tracing-extension-secret-license");
        await().until(extensionStartedFuture::isDone);
        await().until(extensionLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingAdditionalBrokerLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var brokerLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file license.lic is corrupt.");
        K8sUtil.createSecret(client,
                platformNamespace,
                "test-additional-broker-license",
                Map.of("license.lic",
                        Base64.getEncoder()
                                .encodeToString("additional broker license data".getBytes(StandardCharsets.UTF_8))));
        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "license.name=test-additional-broker-license");
        assertLicense("test-additional-broker-license");
        await().until(brokerLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingDataHubLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var dataHubLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file dataHub.plic is corrupt.");
        K8sUtil.createSecret(client,
                platformNamespace,
                "test-data-hub-license",
                Map.of("dataHub.plic",
                        Base64.getEncoder().encodeToString("data hub license data".getBytes(StandardCharsets.UTF_8))));
        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "license.name=test-data-hub-license");
        assertLicense("test-data-hub-license");
        await().until(dataHubLicenseFuture::isDone);
    }

    private void assertLicense(final @NotNull String licenseSecretName) {
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
