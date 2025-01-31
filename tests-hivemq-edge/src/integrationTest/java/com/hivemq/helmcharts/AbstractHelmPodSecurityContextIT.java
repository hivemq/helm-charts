package com.hivemq.helmcharts;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHelmPodSecurityContextIT extends AbstractHelmSecurityContextIT {

    @Override
    protected @NotNull String operatorChartRootUserValuesFile() {
        return "/files/operator-pod-security-context-root-user-values.yaml";
    }

    @Override
    protected @NotNull String platformChartRootUserValuesFile() {
        return "/files/platform-pod-security-context-root-user-values.yaml";
    }

    @Override
    protected @NotNull String operatorChartNonRootUserValuesFile() {
        return "/files/operator-pod-security-context-non-root-user-values.yaml";
    }

    @Override
    protected @NotNull String platformChartNonRootUserValuesFile() {
        return "/files/platform-pod-security-context-non-root-user-values.yaml";
    }
}
