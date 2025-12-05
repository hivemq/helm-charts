package com.hivemq.helmcharts.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmApplyCrdDisabledIT extends AbstractHelmApplyCrdIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCrdNotDeployed_withDisabledCrdApply_operatorIsFailing() throws Exception {
        final var crdApplyDisabled = waitForOperatorLog(".*Apply HiveMQ Platform CRD: false");
        final var crdWaiting =
                waitForOperatorLog(".*Waiting .* ms for HiveMQ Platform CRD '%s' to become ready...".formatted(CRD_NAME));
        final var crdReadyFailed =
                waitForOperatorLog(".*Could not verify ready status of applied HiveMQ Platform CRD '%s'".formatted(
                        CRD_NAME));

        // installOperatorChart() blocks until the Operator is ready, so we have to call it async
        final var additionalCommands =
                List.of("--skip-crds", "--set", "crd.apply=false", "--set", "crd.waitTimeout=PT1S")
                        .toArray(new String[0]);
        final var uncaughtExceptionRef = new AtomicReference<Exception>();
        final var operatorInstallThread = new Thread(() -> {
            try {
                installPlatformOperatorChartAndWaitToBeRunning(additionalCommands);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                uncaughtExceptionRef.set(e);
            }
        });
        operatorInstallThread.setName("operator-install");
        operatorInstallThread.setDaemon(true);
        try {
            operatorInstallThread.start();

            await().atMost(ONE_MINUTE).until(crdApplyDisabled::isDone);
            await().atMost(ONE_MINUTE).until(crdWaiting::isDone);
            await().atMost(ONE_MINUTE).until(crdReadyFailed::isDone);
        } finally {
            operatorInstallThread.interrupt();
            operatorInstallThread.join();
        }
        assertThat(uncaughtExceptionRef).hasNullValue();
    }
}
