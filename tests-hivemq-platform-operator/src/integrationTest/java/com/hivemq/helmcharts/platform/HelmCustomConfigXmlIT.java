package com.hivemq.helmcharts.platform;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmCustomConfigXmlIT extends AbstractHelmChartIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomXml_hivemqRunning() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning();
        installPlatformChartAndWaitToBeRunning("--set-file",
                "config.overrideHiveMQConfig=/files/hivemq-config-override.xml");

        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var configmap = client.configMaps()
                    .inNamespace(platformNamespace)
                    .withName("hivemq-configuration-" + PLATFORM_RELEASE_NAME)
                    .get();
            assertThat(configmap).isNotNull();
            final var xmlConfig = configmap.getData().get("config.xml");
            assertThat(xmlConfig).isNotNull().contains("<port>1884</port>");
        });
    }
}
