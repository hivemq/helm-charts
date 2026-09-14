package com.hivemq.helmcharts.operator;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CustomOperatorSelectorIT extends AbstractHelmChartIT {

    private final @NotNull String platformNameAlpha = platformReleaseName + "-alpha";
    private final @NotNull String platformNameBeta = platformReleaseName + "-beta";

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
        helmChartContainer.uninstallRelease(platformNameAlpha, platformNamespace);
        helmChartContainer.uninstallRelease(platformNameBeta, platformNamespace, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorSelectorIsConfigured_thenOnlyMatchingCustomResourcesAreReconciled() throws Exception {
        // the operator should reconcile the alpha platform, but ignore the beta platform
        installPlatformOperatorChartAndWaitToBeRunning("--set", "selector=alpha");

        installPlatformChart(platformNameAlpha, "--set", "nodes.replicaCount=1", "--set", "operator.selector=alpha");
        installPlatformChart(platformNameBeta, "--set", "nodes.replicaCount=1", "--set", "operator.selector=beta");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformNameAlpha);

        // assert that all custom resources are present, but only one StatefulSet was created
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, platformNameAlpha).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, platformNameBeta).get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(platformNamespace).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(platformNameAlpha));
    }
}
