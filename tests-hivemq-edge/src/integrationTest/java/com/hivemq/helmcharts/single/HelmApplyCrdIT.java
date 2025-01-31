package com.hivemq.helmcharts.single;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

@Tag("ApplyCrd")
class HelmApplyCrdIT extends AbstractHelmChartIT {

    private static final @NotNull String CRD_NAME = "hivemq-platforms.hivemq.com";
    private static final @NotNull String CRD_VERSION = "v1";

    private final @NotNull JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);

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

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCrdNotDeployed_withDisabledCrdApply_operatorIsFailing() throws Exception {
        final var crdApplyDisabled = waitForOperatorLog(".*Apply HiveMQ Platform CRD: false");
        final var crdWaiting = waitForOperatorLog(String.format(
                ".*Waiting .* ms for HiveMQ Platform CRD '%s' to become ready...",
                CRD_NAME));
        final var crdReadyFailed = waitForOperatorLog(String.format(
                ".*Could not verify ready status of applied HiveMQ Platform CRD '%s'",
                CRD_NAME));

        // installOperatorChart() blocks until the Operator is ready, so we have to call it async
        final var additionalCommands = List.of("--skip-crds",
                "--set",
                "crd.apply=false",
                "--set",
                "crd.waitTimeout=PT1S").toArray(new String[0]);
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

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCrdNotDeployed_operatorIsRunning() throws Exception {
        installAndAssertRunningOperator(".*HiveMQ Platform CRD is not deployed");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withOutdatedCrd_operatorIsRunning() throws Exception {
        final var rootPath = Path.of("..").toAbsolutePath();
        final var crdPath = rootPath.resolve(String.format("charts/hivemq-platform-operator/crds/%s-%s.yml",
                CRD_NAME,
                CRD_VERSION));
        final var crdYaml = Files.readString(crdPath);
        final var crd = client.getKubernetesSerialization().unmarshal(crdYaml, CustomResourceDefinition.class);
        crd.getSpec()
                .getVersions()
                .forEach(version -> version.getSchema()
                        .getOpenAPIV3Schema()
                        .getProperties()
                        .get("status")
                        .getProperties()
                        .get("crdVersion")
                        .setEnum(List.of(jsonNodeFactory.textNode("V1_0_0"), jsonNodeFactory.textNode("V1_0_1"))));

        final var customCrdResource = client.apiextensions().v1().customResourceDefinitions().resource(crd);
        customCrdResource.create();
        customCrdResource.waitUntilCondition(c -> c != null &&
                c.getStatus() != null &&
                c.getStatus().getConditions() != null &&
                c.getStatus()
                        .getConditions()
                        .stream()
                        .filter(condition -> "Established".equals(condition.getType()))
                        .anyMatch(condition -> "True".equals(condition.getStatus())), 10, TimeUnit.SECONDS);

        installAndAssertRunningOperator(
                ".*HiveMQ Platform CRD is not on version .* \\(deployed versions: V1_0_0, V1_0_1\\)");
    }

    private void installAndAssertRunningOperator(final @NotNull String expectedCrdVerifyMessagePattern)
            throws Exception {
        final var crdApplyEnabled = waitForOperatorLog(".*Apply HiveMQ Platform CRD: true");
        final var crdVerify = waitForOperatorLog(expectedCrdVerifyMessagePattern);
        final var crdApplying = waitForOperatorLog(String.format(
                ".*Applying %s HiveMQ Platform CRD '%s' \\(version: .*\\)",
                CRD_VERSION,
                CRD_NAME));
        final var crdWaiting = waitForOperatorLog(String.format(
                ".*Waiting .* ms for HiveMQ Platform CRD '%s' to become ready...",
                CRD_NAME));
        final var crdReady = waitForOperatorLog(String.format(".*HiveMQ Platform CRD '%s' is ready", CRD_NAME));

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
