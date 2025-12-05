package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmBridgeExtensionSecretIT extends AbstractHelmBridgeExtensionIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeSecretConfiguration_messageBridged() throws Exception {
        // create bridge extension configuration as a Secret
        final var bridgeConfiguration =
                readResourceFile("bridge-config.xml").replace("<host>remote</host>", "<host>" + ipAddress + "</host>");
        K8sUtil.createSecret(client, platformNamespace, "test-bridge-configuration", bridgeConfiguration);

        // deploy chart and wait to be ready
        installPlatformChartAndWaitToBeRunning("/files/bridge-with-secret-config-values.yaml");
        assertMessagesBridged();
    }
}
