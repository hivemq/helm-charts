package com.hivemq.helmcharts.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

class HelmApplyCrdInstallIT extends AbstractHelmApplyCrdIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCrdNotDeployed_operatorIsRunning() throws Exception {
        installAndAssertRunningOperator(".*HiveMQ Platform CRD is not deployed");
    }
}
