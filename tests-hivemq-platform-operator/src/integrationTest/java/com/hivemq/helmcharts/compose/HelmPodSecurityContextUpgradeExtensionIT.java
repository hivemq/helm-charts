package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.PodUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Tag("PodSecurityContext")
@SuppressWarnings("DuplicatedCode")
class HelmPodSecurityContextUpgradeExtensionIT extends AbstractHelmChartIT {

    @Override
    protected boolean installOperatorChart() {
        return false;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chartValues")
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void updateExtensionConfigMap_withRootAndNonRootUsers_rollingRestart(final @NotNull ChartValues chartValues)
            throws Exception {
        installOperatorChartAndWaitToBeRunning(chartValues.operator.valuesFile);
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(operatorNamespace,
                operatorLabels,
                "hivemq-platform-operator",
                chartValues.operator.uid,
                chartValues.operator.gid);

        final var tracingConfigMap =
                K8sUtil.createConfigMap(client, platformNamespace, "distributed-tracing-config-map.yml");
        assertThat(tracingConfigMap).isNotNull();
        final var extensionStartedFuture = waitForPlatformLog(
                ".*Extension \"HiveMQ Enterprise Distributed Tracing Extension\" version .* started successfully.");

        installPlatformChartAndWaitToBeRunning(chartValues.platform.valuesFile);
        final var platformLabels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        assertUidAndGid(platformNamespace,
                platformLabels,
                "hivemq",
                chartValues.platform.uid,
                chartValues.platform.gid);
        await().atMost(1, TimeUnit.MINUTES).until(extensionStartedFuture::isDone);

        K8sUtil.updateConfigMap(client, platformNamespace, "distributed-tracing-config-map-update.yml");
        final var configurationUpdatedFuture = waitForPlatformLog(
                ".*HiveMQ Enterprise Distributed Tracing Extension: Successfully updated configuration from '/opt/hivemq/extensions/hivemq-distributed-tracing-extension/conf/config.xml'.");

        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RESTART_EXTENSIONS"),
                3,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getHiveMQPlatformStatus("RUNNING"), 3, TimeUnit.MINUTES);
        await().atMost(1, TimeUnit.MINUTES).until(configurationUpdatedFuture::isDone);
    }

    @SuppressWarnings("SameParameterValue")
    private static void assertUidAndGid(
            final @NotNull String namespace,
            final @NotNull Map<String, String> labels,
            final @NotNull String containerName,
            final int expectedUid,
            final int expectedGid) {
        client.pods().inNamespace(namespace).withLabels(labels).list().getItems().forEach(pod -> {
            final var execResult = PodUtil.execute(client,
                    namespace,
                    pod.getMetadata().getName(),
                    containerName,
                    "sh",
                    "-c",
                    "stat -c \"%u %g\" /proc/1");
            try {
                assertThat(execResult.await(2, TimeUnit.MINUTES)).isTrue();
                assertThat(execResult.getOutput()).isEqualTo(expectedUid + " " + expectedGid);
                assertThat(execResult.getError()).isNull();
            } catch (final Exception e) {
                fail("Could not retrieve UID and GID from pod '%s': %s", pod.getMetadata().getName(), e);
            } finally {
                execResult.close();
            }
        });
    }

    private static Stream<Arguments> chartValues() {
        return Stream.of(arguments(new ChartValues(new Values(0, 0, "/files/operator-root-user-values.yaml"),
                        new Values(0, 0, "/files/platform-root-user-with-tracing-extension-values.yaml"))),
                // Default Operator non-root UID is 185 and GID is 0
                arguments(new ChartValues(new Values(185, 0, "/files/operator-non-root-user-values.yaml"),
                        new Values(10000, // Default Platform non-root UID is 1000 and GID is 0
                                0, "/files/platform-non-root-user-with-tracing-extension-values.yaml"))));
    }

    private record ChartValues(@NotNull Values operator, @NotNull Values platform) {
        @Override
        public String toString() {
            return String.format("Operator values: %s and Platform values: %s", operator, platform);
        }
    }

    private record Values(int uid, int gid, @NotNull String valuesFile) {
        @Override
        public String toString() {
            return String.format("(uid=%s, gid=%s , valuesFile='%s')", uid, gid, valuesFile);
        }
    }
}
