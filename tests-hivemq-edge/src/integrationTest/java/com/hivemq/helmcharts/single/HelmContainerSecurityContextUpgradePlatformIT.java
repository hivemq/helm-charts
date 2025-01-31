package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmContainerSecurityContextIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;

@Tag("ContainerSecurityContext")
@Tag("ContainerSecurityContextUpgradePlatform")
class HelmContainerSecurityContextUpgradePlatformIT extends AbstractHelmContainerSecurityContextIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chartValues")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void updateConfigMap_withRootAndNonRootUsers_rollingRestart(final @NotNull ChartValues chartValues)
            throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning(chartValues.operator().valuesFile());
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(operatorNamespace,
                operatorLabels,
                "hivemq-platform-operator",
                chartValues.operator().uid(),
                chartValues.operator().gid());

        installPlatformChartAndWaitToBeRunning(chartValues.platform().valuesFile());
        final var platformLabels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        assertUidAndGid(platformNamespace,
                platformLabels,
                "hivemq",
                chartValues.platform().uid(),
                chartValues.platform().gid());

        K8sUtil.updateConfigMap(client, platformNamespace, "hivemq-config-map-update.yml");
        K8sUtil.waitForHiveMQPlatformStateRunningAfterRollingRestart(client, platformNamespace, PLATFORM_RELEASE_NAME);
    }
}
