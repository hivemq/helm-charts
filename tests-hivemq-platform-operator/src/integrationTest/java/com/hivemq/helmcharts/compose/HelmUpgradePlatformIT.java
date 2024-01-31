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
class HelmUpgradePlatformIT extends AbstractHelmChartIT {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmUpgradePlatformIT.class);

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withDeployedPlatform_upgradeIncreasingReplicaCount() throws Exception {
        helmChartContainer.installOperatorChart(operatorReleaseName);
        helmChartContainer.installPlatformChart(platformReleaseName,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                namespace);

        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, namespace, platformReleaseName);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 5, TimeUnit.MINUTES);
        LOG.debug("Platform ready");

        helmChartContainer.upgradePlatformChart(platformReleaseName,
                "--set",
                "nodes.replicaCount=2",
                "--namespace",
                namespace);

        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("SCALING"), 2, TimeUnit.MINUTES);
        LOG.debug("Platform scaling");

        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 2, TimeUnit.MINUTES);
        LOG.debug("Platform upgraded");

        final var upgradedStatefulSet =
                client.apps().statefulSets().inNamespace(namespace).withName(platformReleaseName).get();
        assertThat(upgradedStatefulSet.getStatus().getAvailableReplicas()).isEqualTo(2);
    }
}
