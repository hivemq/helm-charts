package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.extensions.CustomTestExtensionMain;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.HiveMQExtension;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.NginxUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

@Tag("Extensions")
class HelmCustomExtensionIT extends AbstractHelmChartIT {

    @TempDir
    private @NotNull Path tmp;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenCustomExtensionIsConfigured_thenStartsUpSuccessfully() throws Exception {
        final var customExtensionZip = HiveMQExtension.createHiveMQExtensionZip(tmp,
                "hivemq-custom-test-extension",
                "HiveMQ Custom Test Extension",
                "1.0.0",
                CustomTestExtensionMain.class);
        NginxUtil.deployNginx(client, namespace, helmChartContainer, List.of(customExtensionZip), true);
        K8sUtil.createSecret(client, namespace, "nginx/nginx-auth-secret.yaml");
        final var extensionStartedFuture = helmChartContainer.getLogWaiter()
                .waitFor("test-hivemq-platform-0",
                        ".*Extension \"HiveMQ Custom Test Extension\" version 1.0.0 started successfully.");

        installChartsAndWaitForPlatformRunning("/files/custom-extension-test-values.yaml");
        await().atMost(1, TimeUnit.MINUTES).until(extensionStartedFuture::isDone);
    }
}
