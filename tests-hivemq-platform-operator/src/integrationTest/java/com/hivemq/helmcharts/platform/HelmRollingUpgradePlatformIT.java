package com.hivemq.helmcharts.platform;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelmRollingUpgradePlatformIT extends AbstractHelmChartIT {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmRollingUpgradePlatformIT.class);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withPreviousPlatformInstalled_upgradeToLatestChartVersion() throws Exception {
        final var currentPlatformChart = helmChartContainer.getPlatformChart();
        final var previousPlatformChart = helmChartContainer.getPreviousPlatformChart();
        assertThat(currentPlatformChart.getVersion()).isNotNull();
        assertThat(currentPlatformChart.getAppVersion()).isNotNull();
        assertThat(previousPlatformChart.getVersion()).isNotNull();
        assertThat(previousPlatformChart.getAppVersion()).isNotNull();
        assertThat(currentPlatformChart.getVersion()) //
                .as("If there was a HiveMQ Platform Chart released recently, make sure to rebase either this branch or its base branch with latest")
                .isGreaterThan(previousPlatformChart.getVersion());
        LOG.info("Current platform chart: {}", helmChartContainer.getPlatformChart());
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
                true,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "image.repository=docker.io/hivemq",
                "--set",
                "image.tag=%s".formatted(currentPlatformChart.getAppVersion()),
                "--namespace",
                platformNamespace,
                // needed to avoid SSA conflicts on label changes in the platform that are managed by the operator
                "--force-conflicts");

        final var platform = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        final var requiresRollingRestart =
                !previousPlatformChart.getAppVersion().equals(currentPlatformChart.getAppVersion());
        if (requiresRollingRestart) {
            // appVersion is tied to the HiveMQ version, so if they are different it should trigger a rolling restart
            platform.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("ROLLING_RESTART"),
                    3,
                    TimeUnit.MINUTES);
        }
        platform.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RUNNING"), 3, TimeUnit.MINUTES);
        final var updatedPodResourceVersion = client.pods()
                .inNamespace(platformNamespace)
                .withName(PLATFORM_RELEASE_NAME + "-0")
                .get()
                .getMetadata()
                .getResourceVersion();

        if (requiresRollingRestart) {
            // make sure the pod was restarted
            assertThat(updatedPodResourceVersion).as(
                            "Expected new Pod resource version field to be different than previous one, as a rolling restart was expected")
                    .isNotEqualTo(currentPodResourceVersion);
            assertThat(client.pods()
                    .inNamespace(platformNamespace)
                    .withName(PLATFORM_RELEASE_NAME + "-0")
                    .get()
                    .getSpec()
                    .getContainers()
                    .getFirst()
                    .getImage()).isEqualTo("docker.io/hivemq/hivemq4:" + currentPlatformChart.getAppVersion());
        } else {
            // make sure no rolling restart was executed
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
