package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

@Tag("Platform")
class HelmPlatformInstallIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withLocalCharts_hivemqRunning() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/platform-values.yaml");
    }
}
