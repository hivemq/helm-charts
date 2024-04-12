package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.PodUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Tag("NonRootUser")
class HelmNonRootUserInstallPlatformIT {

    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();
    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;
    private static final @NotNull String OPERATOR_NAMESPACE = "operator-namespace";
    private static final @NotNull String PLATFORM_NAMESPACE =
            K8sUtil.getNamespaceName(HelmNonRootUserInstallPlatformIT.class);
    private static final @NotNull String PLATFORM_RELEASE_NAME = "test-hivemq-platform";
    private static final @NotNull String OPERATOR_RELEASE_NAME = "test-hivemq-platform-operator";
    private static final int MIN_UID = 1000660000;
    private static final int MAX_UID = 2147483647;

    @BeforeAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseSetup() {
        HELM_CHART_CONTAINER.start();
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseCleanUp() {
        HELM_CHART_CONTAINER.stop();
    }

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void setup() throws Exception {
        HELM_CHART_CONTAINER.createNamespace(OPERATOR_NAMESPACE);
        HELM_CHART_CONTAINER.createNamespace(PLATFORM_NAMESPACE);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void uninstallChartReleases() throws Exception {
        try {
            HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME, PLATFORM_NAMESPACE);
            HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME, OPERATOR_NAMESPACE);
        } finally {
            HELM_CHART_CONTAINER.deleteNamespace(PLATFORM_NAMESPACE);
            HELM_CHART_CONTAINER.deleteNamespace(OPERATOR_NAMESPACE);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withDefaultNonRootUsers_hivemqRunning() throws Exception {
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME,
                "-f",
                "/files/operator-non-root-user-values.yaml",
                "--namespace",
                OPERATOR_NAMESPACE);
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(OPERATOR_NAMESPACE,
                operatorLabels,
                "hivemq-platform-operator",
                185,
                0); // Default UID is 185 and GID is 0;

        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/platform-non-root-user-values.yaml",
                "--namespace",
                PLATFORM_NAMESPACE);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, PLATFORM_NAMESPACE, PLATFORM_RELEASE_NAME);
        final var platformLabels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        assertUidAndGid(PLATFORM_NAMESPACE, //
                platformLabels, //
                "hivemq", //
                10000, //
                0); // Default UID is 10000 and GID is 0;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withRandomNonRootUsers_hivemqRunning() throws Exception {
        final var operatorUid = ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID);
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME,
                "-f",
                "/files/operator-non-root-user-values.yaml",
                "--set",
                "podSecurityContext.runAsUser=" + operatorUid,
                "--namespace",
                OPERATOR_NAMESPACE);
        final var operatorLabels = K8sUtil.getHiveMQPlatformOperatorLabels(OPERATOR_RELEASE_NAME);
        assertUidAndGid(OPERATOR_NAMESPACE, operatorLabels, "hivemq-platform-operator", operatorUid, 0);

        final var platformUid = ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID);
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/platform-non-root-user-values.yaml",
                "--set",
                "nodes.podSecurityContext.runAsUser=" + platformUid,
                "--namespace",
                PLATFORM_NAMESPACE);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, PLATFORM_NAMESPACE, PLATFORM_RELEASE_NAME);
        final var platformLabels = K8sUtil.getHiveMQPlatformLabels(PLATFORM_RELEASE_NAME);
        assertUidAndGid(PLATFORM_NAMESPACE, //
                platformLabels, //
                "hivemq", //
                platformUid, //
                0);
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
}
