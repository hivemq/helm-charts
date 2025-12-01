package com.hivemq.helmcharts.license;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

class HelmDataHubLicensesIT extends AbstractHelmLicensesIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withDataHubLicenseFileContent_statefulSetWithLicenseSecretMounted() {
        final var dataHubLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file dataHub1.plic is corrupt.");
        helmUpgradePlatform.set("license.create", "true")
                .setFile("license.dataHub.dataHub1.overrideLicense", "mock-data-hub-license.plic")
                .call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertLicense("hivemq-license-test-hivemq-platform");
        await().until(dataHubLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingDataHubLicenseFileContent_statefulSetWithLicenseSecretMounted() {
        final var dataHubLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file dataHub.plic is corrupt.");
        K8sUtil.createSecret(client,
                platformNamespace,
                "test-data-hub-license",
                Map.of("dataHub.plic",
                        Base64.getEncoder().encodeToString("data hub license data".getBytes(StandardCharsets.UTF_8))));
        helmUpgradePlatform.set("license.name", "test-data-hub-license").call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertLicense("test-data-hub-license");
        await().until(dataHubLicenseFuture::isDone);
    }
}
