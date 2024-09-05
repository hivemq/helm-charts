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
@Tag("DataHubLicense")
class HelmDataHubLicensesIT extends AbstractHelmLicensesIT {

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
}
