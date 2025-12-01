package com.hivemq.helmcharts.platform;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.marcnuri.helm.Helm;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HelmRollingUpgradePlatformIT extends AbstractHelmChartIT {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmRollingUpgradePlatformIT.class);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withPreviousPlatformInstalled_upgradeToLatestChartVersion() {
        final var previousPlatformChart = getPreviousPlatformChart();
        assertThat(platformChart.getChartVersion()).isNotNull();
        assertThat(platformChart.getAppVersion()).isNotNull();
        assertThat(previousPlatformChart.getChartVersion()).isNotNull();
        assertThat(previousPlatformChart.getAppVersion()).isNotNull();
        assertThat(platformChart.getChartVersion()) //
                .as("If there was a HiveMQ Platform Chart released recently, make sure to rebase either this branch or its base branch with latest")
                .isGreaterThan(previousPlatformChart.getChartVersion());
        LOG.info("Current platform chart: {}", platformChart);
        LOG.info("Previous platform chart: {}", previousPlatformChart);

        // install previous platform chart version released
        Helm.repo().add().withName("hivemq").withUrl(URI.create("https://hivemq.github.io/helm-charts")).call();
        Helm.install("hivemq/hivemq-platform")
                .withName(PLATFORM_RELEASE_NAME)
                .withNamespace(platformNamespace)
                .set("nodes.replicaCount", "1")
                .withVersion(previousPlatformChart.getChartVersion().toString())
                .withKubeConfig(kubeConfigPath)
                .atomic()
                .waitReady()
                .debug()
                .call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        final var currentPodResourceVersion = client.pods()
                .inNamespace(platformNamespace)
                .withName(PLATFORM_RELEASE_NAME + "-0")
                .get()
                .getMetadata()
                .getResourceVersion();

        // upgrade to latest local chart version
        helmUpgradePlatform.set("image.repository", "docker.io/hivemq")
                .set("image.tag", "%s".formatted(platformChart.getAppVersion()))
                .call();

        final var platform = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        final var requiresRollingRestart = !previousPlatformChart.getAppVersion().equals(platformChart.getAppVersion());
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
                    .getImage()).isEqualTo("docker.io/hivemq/hivemq4:" + platformChart.getAppVersion());
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
