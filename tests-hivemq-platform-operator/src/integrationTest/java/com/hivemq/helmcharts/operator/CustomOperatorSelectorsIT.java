package com.hivemq.helmcharts.operator;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CustomOperatorSelectorsIT extends AbstractHelmChartIT {

    private final @NotNull String platformNameAlphaTier1 = platformReleaseName + "-alpha-tier1";
    private final @NotNull String platformNameAlphaTier2 = platformReleaseName + "-alpha-tier2";
    private final @NotNull String platformNameAlphaTier3 = platformReleaseName + "-alpha-tier3";

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Override
    protected boolean uninstallPlatformChart() {
        return false;
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        helmChartContainer.uninstallRelease(platformNameAlphaTier1, platformNamespace);
        helmChartContainer.uninstallRelease(platformNameAlphaTier2, platformNamespace);
        helmChartContainer.uninstallRelease(platformNameAlphaTier3, platformNamespace, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorSelectorIsConfigured_thenOnlyMatchingCustomResourcesAreReconciled() throws Exception {
        // the operator should reconcile the tier two and three platforms, but ignore the tier one platform
        installPlatformOperatorChartAndWaitToBeRunning("--set", "selectors=group=alpha\\,tier!=one");

        installPlatformChart(platformNameAlphaTier1,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "operator.labels.group=alpha,operator.labels.tier=one");
        installPlatformChart(platformNameAlphaTier2,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "operator.labels.group=alpha,operator.labels.tier=two");
        installPlatformChart(platformNameAlphaTier3,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "operator.labels.group=alpha,operator.labels.tier=three");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformNameAlphaTier2);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformNameAlphaTier3);

        // assert that all custom resources are present, but only two StatefulSets were created
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, platformNameAlphaTier1).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, platformNameAlphaTier2).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, platformNameAlphaTier3).get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(platformNamespace).list().getItems()).hasSize(2)
                .satisfiesExactlyInAnyOrder(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                                .isEqualTo(platformNameAlphaTier2),
                        statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                                .isEqualTo(platformNameAlphaTier3));
    }
}
