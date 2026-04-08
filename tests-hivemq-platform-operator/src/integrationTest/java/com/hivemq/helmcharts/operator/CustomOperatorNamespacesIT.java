package com.hivemq.helmcharts.operator;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CustomOperatorNamespacesIT extends AbstractHelmChartIT {

    private final @NotNull String platformNameAlpha = platformReleaseName + "-alpha";
    private final @NotNull String platformNameBeta = platformReleaseName + "-beta";
    private final @NotNull String platformNameGamma = platformReleaseName + "-gamma";

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
        helmChartContainer.uninstallRelease(platformNameAlpha, namespaceAlpha, true);
        helmChartContainer.uninstallRelease(platformNameBeta, namespaceBeta, true);
        helmChartContainer.uninstallRelease(platformNameGamma, namespaceGamma, true);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenOperatorNamespacesIsConfigured_thenOnlyCustomResourcesInWatchedNamespacesReconciled() throws Exception {
        // the operator should reconcile the platforms in the alpha and beta namespace,
        // but ignore the platform in the gamma namespace
        installPlatformOperatorChartAndWaitToBeRunning("--set",
                "namespaces=%s\\,%s".formatted(namespaceAlpha, namespaceBeta));

        helmChartContainer.installPlatformChart(platformNameAlpha,
                "--namespace",
                namespaceAlpha,
                "--set",
                "nodes.replicaCount=1");
        helmChartContainer.installPlatformChart(platformNameBeta,
                "--namespace",
                namespaceBeta,
                "--set",
                "nodes.replicaCount=1");
        helmChartContainer.installPlatformChart(platformNameGamma,
                "--namespace",
                namespaceGamma,
                "--set",
                "nodes.replicaCount=1");
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespaceAlpha, platformNameAlpha);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespaceBeta, platformNameBeta);

        // assert that all custom resources are present, but only two StatefulSets were created
        assertThat(K8sUtil.getHiveMQPlatform(client, namespaceAlpha, platformNameAlpha).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, namespaceBeta, platformNameBeta).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, namespaceGamma, platformNameGamma).get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(namespaceAlpha).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(platformNameAlpha));
        assertThat(client.apps().statefulSets().inNamespace(namespaceBeta).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(platformNameBeta));
        assertThat(client.apps().statefulSets().inNamespace(namespaceGamma).list().getItems()).isEmpty();
    }
}
