package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("CustomOperatorConfig")
@Tag("OperatorSelector")
class CustomOperatorSelectorIT extends AbstractHelmChartIT {

    private static final @NotNull String PLATFORM_NAME_ALPHA = PLATFORM_RELEASE_NAME + "-alpha";
    private static final @NotNull String PLATFORM_NAME_BETA = PLATFORM_RELEASE_NAME + "-beta";

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
        helmChartContainer.uninstallRelease(PLATFORM_NAME_ALPHA, platformNamespace);
        helmChartContainer.uninstallRelease(PLATFORM_NAME_BETA, platformNamespace, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorSelectorIsConfigured_thenOnlyMatchingCustomResourcesAreReconciled() throws Exception {
        // the operator should reconcile the alpha platform, but ignore the beta platform
        installPlatformOperatorChartAndWaitToBeRunning("--set", "selector=alpha");

        installPlatformChart(PLATFORM_NAME_ALPHA, "--set", "nodes.replicaCount=1", "--set", "operator.selector=alpha");
        installPlatformChart(PLATFORM_NAME_BETA, "--set", "nodes.replicaCount=1", "--set", "operator.selector=beta");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_NAME_ALPHA);

        // assert that all custom resources are present, but only one StatefulSet was created
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_NAME_ALPHA).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_NAME_BETA).get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(platformNamespace).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(PLATFORM_NAME_ALPHA));
    }
}
