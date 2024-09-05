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
@Tag("AdditionalBrokerLicense")
class HelmAdditionalBrokerLicensesIT extends AbstractHelmLicensesIT {

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
                "license.additionalLicenses.broker.overrideLicense=/files/mock-additional-license.lic");
        assertLicense("hivemq-license-test-hivemq-platform");
        await().until(brokerLicenseFuture::isDone);
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
}
