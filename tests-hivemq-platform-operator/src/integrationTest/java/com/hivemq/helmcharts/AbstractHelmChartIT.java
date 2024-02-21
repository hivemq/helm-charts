package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public abstract class AbstractHelmChartIT {

    protected final @NotNull Network network = Network.newNetwork();

    @Container
    protected final @NotNull HelmChartContainer helmChartContainer = new HelmChartContainer().withNetwork(network);

    protected final @NotNull String namespace = K8sUtil.getNamespaceName(this.getClass());
    protected final @NotNull String platformReleaseName = getPlatformReleaseName();
    protected final @NotNull String operatorReleaseName = getOperatorReleaseName();

    @SuppressWarnings("NotNullFieldNotInitialized")
    protected @NotNull KubernetesClient client;

    @BeforeEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseSetup() {
        client = helmChartContainer.getKubernetesClient();
        helmChartContainer.createNamespace(namespace);
    }

    @AfterEach
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    final void baseTearDown() throws Exception {
        if (cleanupPlatformChart()) {
            helmChartContainer.uninstallRelease(platformReleaseName,
                    "--cascade",
                    "foreground",
                    "--namespace",
                    namespace);
        }
        if (cleanupNamespace()) {
            helmChartContainer.deleteNamespace(namespace);
        } else {
            // assert that the test cleaned up the namespace on its own
            assertThat(client.namespaces().withName(namespace).get()).isNull();
        }
        if (cleanupOperatorChart()) {
            helmChartContainer.uninstallRelease(operatorReleaseName, "--cascade", "foreground");
        }

        network.close();
    }

    protected @NotNull String getOperatorReleaseName() {
        return "test-hivemq-platform-operator";
    }

    protected @NotNull String getPlatformReleaseName() {
        return "test-hivemq-platform";
    }

    protected boolean cleanupOperatorChart() {
        return true;
    }

    protected boolean cleanupPlatformChart() {
        return true;
    }

    protected boolean cleanupNamespace() {
        return true;
    }

    protected void installChartsAndWaitForPlatformRunning(final @NotNull String valuesResourceFile) throws Exception {
        installChartsAndWaitForPlatformRunning("-f", valuesResourceFile, "--namespace", namespace);
    }

    protected void installChartsAndWaitForPlatformRunning(
            final @NotNull String valuesResourceFile, final @NotNull String setFileOverride) throws Exception {
        installChartsAndWaitForPlatformRunning("--set-file",
                setFileOverride,
                "-f",
                valuesResourceFile,
                "--namespace",
                namespace);
    }

    protected void installChartsAndWaitForPlatformRunning(
            final @NotNull String... commands) throws Exception {
        helmChartContainer.installOperatorChart(operatorReleaseName);
        helmChartContainer.installPlatformChart(platformReleaseName, commands);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, namespace, platformReleaseName);
    }
}
