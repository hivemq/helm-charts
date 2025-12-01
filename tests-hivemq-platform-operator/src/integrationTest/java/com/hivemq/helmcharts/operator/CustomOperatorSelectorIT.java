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
    void tearDown() {
        Helm.uninstall(PLATFORM_NAME_ALPHA).withNamespace(platformNamespace).call();
        Helm.uninstall(PLATFORM_NAME_BETA).withNamespace(platformNamespace).call();
        helmChartContainer.deleteNamespace(platformNamespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorSelectorIsConfigured_thenOnlyMatchingCustomResourcesAreReconciled() {
        // the operator should reconcile the alpha platform, but ignore the beta platform
        final var operatorRelease = helmUpgradePlatformOperator.set("selector", "alpha").call();
        assertThat(operatorRelease).returns("deployed", Release::getStatus);
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, OPERATOR_RELEASE_NAME);

        final var alphaRelease =
                helmUpgradePlatform.withName(PLATFORM_NAME_ALPHA).set("operator.selector", "alpha").call();
        assertThat(alphaRelease).returns("deployed", Release::getStatus);

        final var betaRelease =
                helmUpgradePlatform.withName(PLATFORM_NAME_BETA).set("operator.selector", "beta").call();
        assertThat(betaRelease).returns("deployed", Release::getStatus);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_NAME_ALPHA);

        // assert that all custom resources are present, but only one StatefulSet was created
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_NAME_ALPHA).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_NAME_BETA).get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(platformNamespace).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(PLATFORM_NAME_ALPHA));
    }
}
