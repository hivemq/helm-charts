package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmLicensesIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Tag("Licenses")
@Tag("ExtensionsLicense")
class HelmExtensionLicensesIT extends AbstractHelmLicensesIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExtensionLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        K8sUtil.createConfigMap(client, platformNamespace, "distributed-tracing-config-map.yml");

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
    void withExistingExtensionLicenseSecret_statefulSetWithLicenseSecretMounted() throws Exception {
        K8sUtil.createConfigMap(client, platformNamespace, "distributed-tracing-config-map.yml");

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
}
