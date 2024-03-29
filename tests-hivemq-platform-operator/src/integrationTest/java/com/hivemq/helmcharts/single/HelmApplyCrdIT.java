package com.hivemq.helmcharts.single;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.testcontainer.LogWaiterUtil;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

@Tag("ApplyCrd")
class HelmApplyCrdIT {

    private static final @NotNull String OPERATOR_RELEASE_NAME = "platform-operator";
    private static final @NotNull String CRD_NAME = "hivemq-platforms.hivemq.com";
    private static final @NotNull String CRD_VERSION = "v1";

    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();

    private final @NotNull LogWaiterUtil logWaiter = HELM_CHART_CONTAINER.getLogWaiter();
    private final @NotNull JsonNodeFactory jsonNodeFactory = new JsonNodeFactory(false);

    private @NotNull KubernetesClient client;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void beforeAll() {
        HELM_CHART_CONTAINER.start();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void afterAll() {
        HELM_CHART_CONTAINER.stop();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setUp() {
        client = HELM_CHART_CONTAINER.getKubernetesClient();

        final var crdResource = client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME);
        crdResource.delete();
        crdResource.waitUntilCondition(Objects::isNull, 10, TimeUnit.SECONDS);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                "default");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCrdNotDeployed_withDisabledCrdApply_operatorIsFailing() throws Exception {
        final var prefix = "hivemq-platform-operator-.*";
        final var crdApplyDisabled = logWaiter.waitFor(prefix, ".*Apply HiveMQ Platform CRD: false");
        final var crdWaiting = logWaiter.waitFor(prefix,
                String.format(".*Waiting .* ms for HiveMQ Platform CRD '%s' to become ready...", CRD_NAME));
        final var crdReadyFailed = logWaiter.waitFor(prefix,
                String.format(".*Could not verify ready status of applied HiveMQ Platform CRD '%s'", CRD_NAME));

        // installOperatorChart() blocks until the Operator is ready, so we have to call it async
        final var additionalCommands = List.of("--skip-crds",
                "--set",
                "env[0].name=hivemq.platform.operator.crd.apply",
                "--set",
                "env[0].value='false'",
                "--set",
                "env[1].name=hivemq.platform.operator.crd.wait-until-ready.timeout",
                "--set",
                "env[1].value=1s").toArray(new String[0]);
        final var uncaughtExceptionRef = new AtomicReference<Exception>();
        final var operatorInstallThread = new Thread(() -> {
            try {
                HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME, additionalCommands);
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

            await().atMost(1, TimeUnit.MINUTES).until(crdApplyDisabled::isDone);
            await().atMost(1, TimeUnit.MINUTES).until(crdWaiting::isDone);
            await().atMost(1, TimeUnit.MINUTES).until(crdReadyFailed::isDone);
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
        final var prefix = "hivemq-platform-operator-.*";
        final var crdApplyEnabled = logWaiter.waitFor(prefix, ".*Apply HiveMQ Platform CRD: true");
        final var crdVerify = logWaiter.waitFor(prefix, expectedCrdVerifyMessagePattern);
        final var crdApplying = logWaiter.waitFor(prefix,
                String.format(".*Applying %s HiveMQ Platform CRD '%s' \\(version: .*\\)", CRD_VERSION, CRD_NAME));
        final var crdWaiting = logWaiter.waitFor(prefix,
                String.format(".*Waiting .* ms for HiveMQ Platform CRD '%s' to become ready...", CRD_NAME));
        final var crdReady = logWaiter.waitFor(prefix, String.format(".*HiveMQ Platform CRD '%s' is ready", CRD_NAME));

        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME, "--skip-crds");
        await().atMost(1, TimeUnit.MINUTES).until(crdApplyEnabled::isDone);
        await().atMost(1, TimeUnit.MINUTES).until(crdVerify::isDone);
        await().atMost(1, TimeUnit.MINUTES).until(crdApplying::isDone);
        await().atMost(1, TimeUnit.MINUTES).until(crdWaiting::isDone);
        await().atMost(1, TimeUnit.MINUTES).until(crdReady::isDone);

        client.apps()
                .deployments()
                .inNamespace("default")
                .withName("hivemq-" + OPERATOR_RELEASE_NAME)
                .waitUntilCondition(d -> d.getStatus() != null && d.getStatus().getAvailableReplicas() == 1,
                        3,
                        TimeUnit.MINUTES);
    }
}
