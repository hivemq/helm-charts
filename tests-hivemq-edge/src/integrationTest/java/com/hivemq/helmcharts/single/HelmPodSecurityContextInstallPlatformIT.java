package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmPodSecurityContextIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Tag("PodSecurityContext")
@Tag("PodSecurityContextInstallPlatform")
class HelmPodSecurityContextInstallPlatformIT extends AbstractHelmPodSecurityContextIT {

    private static final int MIN_UID = 1000660000;
    private static final int MAX_UID = 2147483647;

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chartValues")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withRootAndNonRootUsers_hivemqRunning(final @NotNull ChartValues chartValues)
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
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withRandomNonRootUsers_hivemqRunning() throws Exception {
        final var operatorUid = ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID);
        installPlatformOperatorChartAndWaitToBeRunning("-f",
                operatorChartNonRootUserValuesFile(),
                "--set",
                "podSecurityContext.runAsUser=" + operatorUid);
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(operatorNamespace, operatorLabels, "hivemq-platform-operator", operatorUid, 0);

        final var platformUid = ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID);
        installPlatformChartAndWaitToBeRunning("-f",
                platformChartNonRootUserValuesFile(),
                "--set",
                "nodes.podSecurityContext.runAsUser=" + platformUid);
        final var platformLabels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        assertUidAndGid(platformNamespace, //
                platformLabels, //
                "hivemq", //
                platformUid, //
                0);
    }
}
