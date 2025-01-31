package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("CustomOperatorConfig")
@Tag("OperatorNamespaces")
class CustomOperatorNamespacesIT extends AbstractHelmChartIT {

    private static final @NotNull String PLATFORM_NAME_ALPHA = PLATFORM_RELEASE_NAME + "-alpha";
    private static final @NotNull String PLATFORM_NAME_BETA = PLATFORM_RELEASE_NAME + "-beta";
    private static final @NotNull String PLATFORM_NAME_GAMMA = PLATFORM_RELEASE_NAME + "-gamma";

    private @NotNull String namespaceAlpha;
    private @NotNull String namespaceBeta;
    private @NotNull String namespaceGamma;

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
        namespaceAlpha = platformNamespace + "-alpha";
        namespaceBeta = platformNamespace + "-beta";
        namespaceGamma = platformNamespace + "-gamma";
        helmChartContainer.createNamespace(namespaceAlpha);
        helmChartContainer.createNamespace(namespaceBeta);
        helmChartContainer.createNamespace(namespaceGamma);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tearDown() throws Exception {
        helmChartContainer.uninstallRelease(PLATFORM_NAME_ALPHA, namespaceAlpha, true);
        helmChartContainer.uninstallRelease(PLATFORM_NAME_BETA, namespaceBeta, true);
        helmChartContainer.uninstallRelease(PLATFORM_NAME_GAMMA, namespaceGamma, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorNamespacesIsConfigured_thenOnlyCustomResourcesInWatchedNamespacesReconciled() throws Exception {
        // the operator should reconcile the platforms in the alpha and beta namespace,
        // but ignore the platform in the gamma namespace
        installPlatformOperatorChartAndWaitToBeRunning("--set",
                String.format("namespaces=%s\\,%s", namespaceAlpha, namespaceBeta));

        helmChartContainer.installPlatformChart(PLATFORM_NAME_ALPHA,
                "--namespace",
                namespaceAlpha,
                "--set",
                "nodes.replicaCount=1");
        helmChartContainer.installPlatformChart(PLATFORM_NAME_BETA,
                "--namespace",
                namespaceBeta,
                "--set",
                "nodes.replicaCount=1");
        helmChartContainer.installPlatformChart(PLATFORM_NAME_GAMMA,
                "--namespace",
                namespaceGamma,
                "--set",
                "nodes.replicaCount=1");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespaceAlpha, PLATFORM_NAME_ALPHA);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespaceBeta, PLATFORM_NAME_BETA);

        // assert that all custom resources are present, but only two StatefulSets were created
        assertThat(K8sUtil.getHiveMQPlatform(client, namespaceAlpha, PLATFORM_NAME_ALPHA).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, namespaceBeta, PLATFORM_NAME_BETA).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, namespaceGamma, PLATFORM_NAME_GAMMA).get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(namespaceAlpha).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(PLATFORM_NAME_ALPHA));
        assertThat(client.apps().statefulSets().inNamespace(namespaceBeta).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(PLATFORM_NAME_BETA));
        assertThat(client.apps().statefulSets().inNamespace(namespaceGamma).list().getItems()).isEmpty();
    }
}
