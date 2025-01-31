package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmPodSecurityContextIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

@Tag("PodSecurityContext")
class HelmPodSecurityContextUpgradeExtensionIT extends AbstractHelmPodSecurityContextIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Override
    protected @NotNull String platformChartRootUserValuesFile() {
        return "/files/platform-pod-security-context-root-user-with-tracing-extension-values.yaml";
    }

    @Override
    protected @NotNull String platformChartNonRootUserValuesFile() {
        return "/files/platform-pod-security-context-non-root-user-with-tracing-extension-values.yaml";
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chartValues")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void updateExtensionConfigMap_withRootAndNonRootUsers_rollingRestart(final @NotNull ChartValues chartValues)
            throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning(chartValues.operator().valuesFile());
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(operatorNamespace,
                operatorLabels,
                "hivemq-platform-operator",
                chartValues.operator().uid(),
                chartValues.operator().gid());

        K8sUtil.createConfigMap(client, platformNamespace, "distributed-tracing-config-map.yml");
        final var extensionStartedFuture = waitForPlatformLog(
                ".*Extension \"HiveMQ Enterprise Distributed Tracing Extension\" version .* started successfully.");

        installPlatformChartAndWaitToBeRunning(chartValues.platform().valuesFile());
        final var platformLabels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        assertUidAndGid(platformNamespace,
                platformLabels,
                "hivemq",
                chartValues.platform().uid(),
                chartValues.platform().gid());
        await().atMost(ONE_MINUTE).until(extensionStartedFuture::isDone);

        K8sUtil.updateConfigMap(client, platformNamespace, "distributed-tracing-config-map-update.yml");
        final var configurationUpdatedFuture = waitForPlatformLog(
                ".*HiveMQ Enterprise Distributed Tracing Extension: Successfully updated configuration from '/opt/hivemq/extensions/hivemq-distributed-tracing-extension/conf/config.xml'.");

        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RESTART_EXTENSIONS"),
                3,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RUNNING"),
                3,
                TimeUnit.MINUTES);
        await().atMost(ONE_MINUTE).until(configurationUpdatedFuture::isDone);
    }
}
