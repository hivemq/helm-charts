package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmBridgeExtensionConfigMapIT extends AbstractHelmBridgeExtensionIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withBridgeConfiguration_messageBridged() throws Exception {
        // create bridge extension configuration as a ConfigMap
        final var bridgeConfiguration =
                readResourceFile("bridge-config.xml").replace("<host>remote</host>", "<host>" + ipAddress + "</host>");
        K8sUtil.createConfigMap(client, platformNamespace, "test-bridge-configuration", bridgeConfiguration);

        // deploy chart and wait to be ready
        installPlatformChartAndWaitToBeRunning("/files/bridge-values.yaml");
        assertMessagesBridged();
    }
}
