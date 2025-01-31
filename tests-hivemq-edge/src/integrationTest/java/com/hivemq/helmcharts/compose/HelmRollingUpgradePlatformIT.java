package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("Upgrade")
class HelmRollingUpgradePlatformIT extends AbstractHelmChartIT {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmRollingUpgradePlatformIT.class);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withPreviousPlatformInstalled_upgradeToLatestChartVersion() throws Exception {
        final var currentPlatformChart = helmChartContainer.getCurrentPlatformChart();
        final var previousPlatformChart = helmChartContainer.getPreviousPlatformChart();
        assertThat(currentPlatformChart.getVersion()).isNotNull();
        assertThat(currentPlatformChart.getAppVersion()).isNotNull();
        assertThat(previousPlatformChart.getVersion()).isNotNull();
        assertThat(previousPlatformChart.getAppVersion()).isNotNull();
        assertThat(currentPlatformChart.getVersion()).isGreaterThan(previousPlatformChart.getVersion());
        LOG.info("Current platform chart: {}", helmChartContainer.getCurrentPlatformChart());
        LOG.info("Previous platform chart: {}", helmChartContainer.getPreviousPlatformChart());

        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                false,
                "--set",
                "nodes.replicaCount=1",
                "--version",
                previousPlatformChart.getVersion().toString(),
                "--namespace",
                platformNamespace);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        final var currentPodResourceVersion = client.pods()
                .inNamespace(platformNamespace)
                .withName(PLATFORM_RELEASE_NAME + "-0")
                .get()
                .getMetadata()
                .getResourceVersion();

        helmChartContainer.upgradePlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        final var requiresRollingRestart =
                !previousPlatformChart.getAppVersion().equals(currentPlatformChart.getAppVersion());
        if (requiresRollingRestart) {
            // appVersion is tied to the HiveMQ version, so if they are different it should trigger a rolling restart
            hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("ROLLING_RESTART"),
                    3,
                    TimeUnit.MINUTES);
        }
        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RUNNING"), 3, TimeUnit.MINUTES);
        final var updatedPodResourceVersion = client.pods()
                .inNamespace(platformNamespace)
                .withName(PLATFORM_RELEASE_NAME + "-0")
                .get()
                .getMetadata()
                .getResourceVersion();

        if (requiresRollingRestart) {
            assertThat(updatedPodResourceVersion).as(
                            "Expected new Pod resource version field to be different than previous one, as a rolling restart was expected")
                    .isNotEqualTo(currentPodResourceVersion); // make sure the pod was restarted
            assertThat(client.pods()
                    .inNamespace(platformNamespace)
                    .withName(PLATFORM_RELEASE_NAME + "-0")
                    .get()
                    .getSpec()
                    .getContainers()
                    .getFirst()
                    .getImage()).isEqualTo("docker.io/hivemq/hivemq4:" + currentPlatformChart.getAppVersion());
        } else {
            // Make sure no rolling restart was executed.
            assertThat(updatedPodResourceVersion).as(
                            "Expected new Pod resource version field to be equal as previous one, as no rolling restart/upgrade was expected")
                    .isEqualTo(currentPodResourceVersion);
            assertThat(client.pods()
                    .inNamespace(platformNamespace)
                    .withName(PLATFORM_RELEASE_NAME + "-0")
                    .get()
                    .getSpec()
                    .getContainers()
                    .getFirst()
                    .getImage()).isEqualTo("docker.io/hivemq/hivemq4:" + previousPlatformChart.getAppVersion());
        }
    }
}
