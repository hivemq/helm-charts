package com.hivemq.helmcharts.license;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

class HelmExtensionLicensesIT extends AbstractHelmLicensesIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExtensionLicenseFileContent_statefulSetWithLicenseSecretMounted() throws Exception {
        K8sUtil.createConfigMap(client, platformNamespace, "distributed-tracing-config-map.yml");

        final var extensionStartedFuture = waitForPlatformLog(
                ".*Extension \"HiveMQ Enterprise Distributed Tracing Extension\" version .* started successfully.");
        final var extensionLicenseFuture =
                logWaiter.waitFor(platformLogWaiterPrefix, ".*License file tracing.elic is corrupt.");

        installPlatformChartAndWaitToBeRunning("-f",
                "/files/distributed-tracing-extension-values.yaml",
                "--set",
                "license.create=true",
                "--set-file",
                "license.extensions.tracing.overrideLicense=/files/mock-extension-license.elic");
        assertLicense("hivemq-license-" + platformReleaseName);
        await().until(extensionStartedFuture::isDone);
        await().until(extensionLicenseFuture::isDone);
    }
}
