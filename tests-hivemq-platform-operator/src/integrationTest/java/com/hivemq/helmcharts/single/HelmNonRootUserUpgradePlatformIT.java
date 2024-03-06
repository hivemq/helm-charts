package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

@Tag("NonRootUser")
class HelmNonRootUserUpgradePlatformIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void updateConfigMap_withNonRootUser_rollingRestart() throws Exception {
        helmChartContainer.installOperatorChart(OPERATOR_RELEASE_NAME,
                "-f",
                "/files/operator-non-root-user-values.yaml");
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/platform-non-root-user-values.yaml",
                "--namespace",
                namespace);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, PLATFORM_RELEASE_NAME);

        K8sUtil.updateConfigMap(client, namespace, "hivemq-config-map-update.yml");
        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, namespace, PLATFORM_RELEASE_NAME);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("ROLLING_RESTART"),
                3,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 3, TimeUnit.MINUTES);
    }
}
