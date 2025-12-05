package com.hivemq.helmcharts.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

class HelmBrokerLicensesIT extends AbstractHelmLicensesIT {

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
}
