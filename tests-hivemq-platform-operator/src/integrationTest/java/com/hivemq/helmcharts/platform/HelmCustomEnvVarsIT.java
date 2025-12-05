package com.hivemq.helmcharts.platform;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmCustomEnvVarsIT extends AbstractHelmChartIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomEnvVars_hivemqRunning() throws Exception {
        final var operatorStartedFuture = waitForOperatorLog(
                ".*Registered reconciler: 'hivemq-controller' for resource: 'class com.hivemq.platform.operator.v1.HiveMQPlatform' for namespace\\(s\\): \\[%s\\]".formatted(
                        platformNamespace));
        installPlatformOperatorChartAndWaitToBeRunning("--set", "namespaces=%s".formatted(platformNamespace));
        await().atMost(ONE_MINUTE).until(operatorStartedFuture::isDone);

        installPlatformChartAndWaitToBeRunning("/files/custom-platform-env-vars-values.yaml");

        // assert the custom operator configuration
        final var operatorDeployment = K8sUtil.getDeployment(client, operatorNamespace, getOperatorName());
        assertThat(operatorDeployment.getSpec().getTemplate().getSpec().getContainers().getFirst().getEnv()) //
                .anyMatch(envVar -> "HIVEMQ_PLATFORM_OPERATOR_NAMESPACES".equals(envVar.getName()) &&
                        platformNamespace.equals(envVar.getValue()));

        // assert the custom platform configuration
        final var statefulSet = K8sUtil.getStatefulSet(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertThat(K8sUtil.getHiveMQContainer(statefulSet.getSpec())
                .getEnv()).anyMatch(envVar -> "MY_CUSTOM_ENV_VAR".equals(envVar.getName()) &&
                "mycustomvalue".equals(envVar.getValue()));
    }
}
