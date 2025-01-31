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
@Tag("OperatorSelectors")
class CustomOperatorSelectorsIT extends AbstractHelmChartIT {

    private static final @NotNull String PLATFORM_NAME_ALPHA_TIER_1 = PLATFORM_RELEASE_NAME + "-alpha-tier1";
    private static final @NotNull String PLATFORM_NAME_ALPHA_TIER_2 = PLATFORM_RELEASE_NAME + "-alpha-tier2";
    private static final @NotNull String PLATFORM_NAME_ALPHA_TIER_3 = PLATFORM_RELEASE_NAME + "-alpha-tier3";

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
        helmChartContainer.uninstallRelease(PLATFORM_NAME_ALPHA_TIER_1, platformNamespace);
        helmChartContainer.uninstallRelease(PLATFORM_NAME_ALPHA_TIER_2, platformNamespace);
        helmChartContainer.uninstallRelease(PLATFORM_NAME_ALPHA_TIER_3, platformNamespace, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorSelectorIsConfigured_thenOnlyMatchingCustomResourcesAreReconciled() throws Exception {
        // the operator should reconcile the tier two and three platforms, but ignore the tier one platform
        installPlatformOperatorChartAndWaitToBeRunning("--set", "selectors=group=alpha\\,tier!=one");

        installPlatformChart(PLATFORM_NAME_ALPHA_TIER_1,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "operator.labels.group=alpha,operator.labels.tier=one");
        installPlatformChart(PLATFORM_NAME_ALPHA_TIER_2,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "operator.labels.group=alpha,operator.labels.tier=two");
        installPlatformChart(PLATFORM_NAME_ALPHA_TIER_3,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "operator.labels.group=alpha,operator.labels.tier=three");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_NAME_ALPHA_TIER_2);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_NAME_ALPHA_TIER_3);

        // assert that all custom resources are present, but only two StatefulSets were created
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_NAME_ALPHA_TIER_1).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_NAME_ALPHA_TIER_2).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_NAME_ALPHA_TIER_3).get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(platformNamespace).list().getItems()).hasSize(2)
                .satisfiesExactlyInAnyOrder(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                                .isEqualTo(PLATFORM_NAME_ALPHA_TIER_2),
                        statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                                .isEqualTo(PLATFORM_NAME_ALPHA_TIER_3));
    }
}
