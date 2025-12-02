package com.hivemq.helmcharts.securitycontext;

import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;

class HelmContainerSecurityContextUpgradePlatformIT extends AbstractHelmContainerSecurityContextIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chartValues")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void updateConfigMap_withRootAndNonRootUsers_rollingRestart(final @NotNull ChartValues chartValues) {
        helmUpgradePlatformOperator.withValuesFile(VALUES_PATH.resolve(chartValues.operator().valuesFile())).call();
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, OPERATOR_RELEASE_NAME);
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(operatorNamespace,
                operatorLabels,
                "hivemq-platform-operator",
                chartValues.operator().uid(),
                chartValues.operator().gid());

        helmUpgradePlatform.withValuesFile(VALUES_PATH.resolve(chartValues.platform().valuesFile())).call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
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
