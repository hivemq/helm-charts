package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

@Tag("NonRootUser")
class HelmNonRootUserInstallPlatformIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withNonRootUser_hivemqRunning() throws Exception {
        helmChartContainer.installOperatorChart(operatorReleaseName, "-f", "/files/operator-non-root-user-values.yaml");
        helmChartContainer.installPlatformChart(platformReleaseName,
                "-f",
                "/files/platform-non-root-user-values.yaml",
                "--namespace",
                namespace);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, platformReleaseName);
    }
}
