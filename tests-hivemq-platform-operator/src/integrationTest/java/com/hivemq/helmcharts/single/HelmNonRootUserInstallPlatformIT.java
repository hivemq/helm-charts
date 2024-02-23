package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Tag("NonRootUser")
class HelmNonRootUserInstallPlatformIT {

    private static final @NotNull HelmChartContainer HELM_CHART_CONTAINER = new HelmChartContainer();
    @SuppressWarnings("NotNullFieldNotInitialized")
    private static @NotNull KubernetesClient client;
    private static final @NotNull String OPERATOR_NAMESPACE = "default";
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
        HELM_CHART_CONTAINER.createNamespace(PLATFORM_NAMESPACE);
        client = HELM_CHART_CONTAINER.getKubernetesClient();
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void uninstallChartReleases() throws Exception {
        HELM_CHART_CONTAINER.uninstallRelease(PLATFORM_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                PLATFORM_NAMESPACE);
        K8sUtil.waitForAllPodsDeletedInNamespace(client, PLATFORM_NAMESPACE);
        HELM_CHART_CONTAINER.uninstallRelease(OPERATOR_RELEASE_NAME,
                "--cascade",
                "foreground",
                "--namespace",
                OPERATOR_NAMESPACE);
        K8sUtil.waitForAllPodsDeletedInNamespace(client, OPERATOR_NAMESPACE);
    }

    @AfterAll
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    static void baseCleanUp() {
        HELM_CHART_CONTAINER.deleteNamespace(PLATFORM_NAMESPACE);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withDefaultNonRootUsers_hivemqRunning() throws Exception {
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME,
                "-f",
                "/files/operator-non-root-user-values.yaml");
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/platform-non-root-user-values.yaml",
                "--namespace",
                PLATFORM_NAMESPACE);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, PLATFORM_NAMESPACE, PLATFORM_RELEASE_NAME);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void installPlatform_withRandomNonRootUsers_hivemqRunning() throws Exception {
        HELM_CHART_CONTAINER.installOperatorChart(OPERATOR_RELEASE_NAME,
                "-f",
                "/files/operator-non-root-user-values.yaml",
                "--set",
                "podSecurityContext.runAsUser=" + ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID));
        HELM_CHART_CONTAINER.installPlatformChart(PLATFORM_RELEASE_NAME,
                "-f",
                "/files/platform-non-root-user-values.yaml",
                "--set",
                "nodes.podSecurityContext.runAsUser=" + ThreadLocalRandom.current().nextInt(MIN_UID, MAX_UID),
                "--namespace",
                PLATFORM_NAMESPACE);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, PLATFORM_NAMESPACE, PLATFORM_RELEASE_NAME);
    }
}
