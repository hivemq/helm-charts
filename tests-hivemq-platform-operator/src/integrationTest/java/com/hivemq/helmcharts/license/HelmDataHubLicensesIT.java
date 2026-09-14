package com.hivemq.helmcharts.license;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

class HelmDataHubLicensesIT extends AbstractHelmLicensesIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withDataHubLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        final var dataHubLicenseFuture =
                logWaiter.waitFor(platformLogWaiterPrefix, ".*License file dataHub1.plic is corrupt.");
        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "license.create=true",
                "--set-file",
                "license.dataHub.dataHub1.overrideLicense=/files/mock-data-hub-license.plic");
        assertLicense("hivemq-license-" + platformReleaseName);
        await().until(dataHubLicenseFuture::isDone);
    }
}
