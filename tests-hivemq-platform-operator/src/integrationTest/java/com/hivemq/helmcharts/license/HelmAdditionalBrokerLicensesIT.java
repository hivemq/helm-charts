package com.hivemq.helmcharts.license;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

class HelmAdditionalBrokerLicensesIT extends AbstractHelmLicensesIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    @Disabled("Re-enable once Helm Java client supports .setFile feature")
    void withAdditionalBrokerLicenseFileContent_statefulSetWithLicenseSecretMounted() {
        final var brokerLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file broker.lic is corrupt.");
        helmUpgradePlatform.set("license.create", "true")
                //TODO: uncomment it out, once Helm Java client supports .setFile feature
                //.setFile("license.additionalLicenses.broker.overrideLicense", "mock-additional-license.lic")
                .call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertLicense("hivemq-license-test-hivemq-platform");
        await().until(brokerLicenseFuture::isDone);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingAdditionalBrokerLicenseFileContent_statefulSetWithLicenseSecretMounted() {
        final var brokerLicenseFuture =
                logWaiter.waitFor(PLATFORM_LOG_WAITER_PREFIX, ".*License file license.lic is corrupt.");
        K8sUtil.createSecret(client,
                platformNamespace,
                "test-additional-broker-license",
                Map.of("license.lic",
                        Base64.getEncoder()
                                .encodeToString("additional broker license data".getBytes(StandardCharsets.UTF_8))));
        helmUpgradePlatform.set("license.name", "test-additional-broker-license").call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertLicense("test-additional-broker-license");
        await().until(brokerLicenseFuture::isDone);
    }
}
