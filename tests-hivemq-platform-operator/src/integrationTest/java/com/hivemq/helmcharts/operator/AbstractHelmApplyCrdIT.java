package com.hivemq.helmcharts.operator;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class AbstractHelmApplyCrdIT extends AbstractHelmChartIT {

    static final @NotNull String CRD_NAME = "hivemq-platforms.hivemq.com";
    static final @NotNull String CRD_VERSION = "v1";

    final @NotNull JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);

    @Override
    protected boolean createPlatformNamespace() {
        return false;
    }

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Override
    protected boolean uninstallPlatformChart() {
        return false;
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setUp() {
        final var crdResource = client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME);
        crdResource.delete();
        crdResource.waitUntilCondition(Objects::isNull, 10, TimeUnit.SECONDS);
    }

    void installAndAssertRunningOperator(final @NotNull String expectedCrdVerifyMessagePattern) throws Exception {
        final var crdApplyEnabled = waitForOperatorLog(".*Apply HiveMQ Platform CRD: true");
        final var crdVerify = waitForOperatorLog(expectedCrdVerifyMessagePattern);
        final var crdApplying = waitForOperatorLog(".*Applying %s HiveMQ Platform CRD '%s' \\(version: .*\\)".formatted(
                CRD_VERSION,
                CRD_NAME));
        final var crdWaiting =
                waitForOperatorLog(".*Waiting .* ms for HiveMQ Platform CRD '%s' to become ready...".formatted(CRD_NAME));
        final var crdReady = waitForOperatorLog(".*HiveMQ Platform CRD '%s' is ready".formatted(CRD_NAME));

        installPlatformOperatorChartAndWaitToBeRunning(List.of("--skip-crds").toArray(new String[0]));
        await().atMost(ONE_MINUTE).until(crdApplyEnabled::isDone);
        await().atMost(ONE_MINUTE).until(crdVerify::isDone);
        await().atMost(ONE_MINUTE).until(crdApplying::isDone);
        await().atMost(ONE_MINUTE).until(crdWaiting::isDone);
        await().atMost(ONE_MINUTE).until(crdReady::isDone);

        client.apps()
                .deployments()
                .inNamespace(operatorNamespace)
                .withName(getOperatorName())
                .waitUntilCondition(d -> d.getStatus() != null && d.getStatus().getAvailableReplicas() == 1,
                        3,
                        TimeUnit.MINUTES);
    }
}
