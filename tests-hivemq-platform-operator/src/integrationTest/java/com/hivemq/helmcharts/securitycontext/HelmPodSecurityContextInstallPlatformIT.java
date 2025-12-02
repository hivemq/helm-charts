package com.hivemq.helmcharts.securitycontext;

import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

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
    void installPlatform_withRootAndNonRootUsers_hivemqRunning(final @NotNull ChartValues chartValues) {
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
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withRandomNonRootUsers_hivemqRunning() {
        final var operatorUid = ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID);
        helmUpgradePlatformOperator.set("podSecurityContext.runAsUser", operatorUid)
                .withValuesFile(VALUES_PATH.resolve(operatorChartNonRootUserValuesFile()))
                .call();
        K8sUtil.waitForPlatformOperatorPodStateRunning(client, operatorNamespace, OPERATOR_RELEASE_NAME);
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(operatorNamespace, operatorLabels, "hivemq-platform-operator", operatorUid, 0);

        final var platformUid = ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID);
        helmUpgradePlatform.set("nodes.podSecurityContext.runAsUser", platformUid)
                .withValuesFile(VALUES_PATH.resolve(platformChartNonRootUserValuesFile()))
                .call();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);
        final var platformLabels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        assertUidAndGid(platformNamespace, //
                platformLabels, //
                "hivemq", //
                platformUid, //
                0);
    }
}
