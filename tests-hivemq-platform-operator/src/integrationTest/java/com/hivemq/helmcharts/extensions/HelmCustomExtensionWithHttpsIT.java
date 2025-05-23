package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.extensions.custom.CustomTestExtensionMain;
import com.hivemq.helmcharts.util.HiveMQExtension;
import com.hivemq.helmcharts.util.NginxUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmCustomExtensionWithHttpsIT extends AbstractHelmChartIT {

    @TempDir
    private @NotNull Path tmp;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenCustomExtensionIsConfigured_withHttps_thenStartsUpSuccessfully() throws Exception {
        final var customExtensionZip = HiveMQExtension.createHiveMQExtensionZip(tmp,
                "hivemq-custom-test-extension",
                "HiveMQ Custom Test Extension",
                "3.0.0",
                CustomTestExtensionMain.class);
        NginxUtil.deployNginx(client, platformNamespace, helmChartContainer, List.of(customExtensionZip), true, false);
        final var extensionStartedFuture =
                waitForPlatformLog(".*Extension \"HiveMQ Custom Test Extension\" version 3.0.0 started successfully.");

        installPlatformChartAndWaitToBeRunning("/files/custom-extension-values-with-https.yaml");
        await().atMost(ONE_MINUTE).until(extensionStartedFuture::isDone);
    }
}
