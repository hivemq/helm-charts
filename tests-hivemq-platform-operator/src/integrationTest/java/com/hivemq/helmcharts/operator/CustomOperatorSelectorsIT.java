package com.hivemq.helmcharts.operator;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.marcnuri.helm.Helm;
import com.marcnuri.helm.Release;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
    void tearDown() {
        Helm.uninstall(PLATFORM_NAME_ALPHA_TIER_1).withNamespace(platformNamespace).call();
        Helm.uninstall(PLATFORM_NAME_ALPHA_TIER_2).withNamespace(platformNamespace).call();
        Helm.uninstall(PLATFORM_NAME_ALPHA_TIER_3).withNamespace(platformNamespace).call();
        helmChartContainer.deleteNamespace(platformNamespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorSelectorIsConfigured_thenOnlyMatchingCustomResourcesAreReconciled() {
        // the operator should reconcile the tier two and three platforms, but ignore the tier one platform
        final var operatorRelease = helmUpgradePlatformOperator.set("selectors", "group=alpha\\,tier!=one").call();
        assertThat(operatorRelease).returns("deployed", Release::getStatus);
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, OPERATOR_RELEASE_NAME);

        final var tier1Release = helmUpgradePlatform.withName(PLATFORM_NAME_ALPHA_TIER_1)
                .set("operator.labels.group", "alpha,operator.labels.tier=one")
                .call();
        assertThat(tier1Release).returns("deployed", Release::getStatus);

        final var tier2Release = helmUpgradePlatform.withName(PLATFORM_NAME_ALPHA_TIER_2)
                .set("operator.labels.group", "alpha,operator.labels.tier=two")
                .call();
        assertThat(tier2Release).returns("deployed", Release::getStatus);

        final var tier3Release = helmUpgradePlatform.withName(PLATFORM_NAME_ALPHA_TIER_3)
                .set("operator.labels.group", "alpha,operator.labels.tier=three")
                .call();
        assertThat(tier3Release).returns("deployed", Release::getStatus);

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
