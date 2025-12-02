package com.hivemq.helmcharts.securitycontext;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractHelmContainerSecurityContextIT extends AbstractHelmSecurityContextIT {

    @Override
    protected @NotNull String operatorChartRootUserValuesFile() {
        return "operator-container-security-context-root-user-values.yaml";
    }

    @Override
    protected @NotNull String platformChartRootUserValuesFile() {
        return "platform-container-security-context-root-user-values.yaml";
    }

    @Override
    protected @NotNull String operatorChartNonRootUserValuesFile() {
        return "operator-container-security-context-non-root-user-values.yaml";
    }

    @Override
    protected @NotNull String platformChartNonRootUserValuesFile() {
        return "platform-container-security-context-non-root-user-values.yaml";
    }
}
