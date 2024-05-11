package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("RollingUpgrades")
@Testcontainers
class StatefulSetRollingUpgradeIT {

    private static final @NotNull String CHART_NAME = "local-hivemq";

    @Container
    private final @NotNull OperatorHelmChartContainer container =
            new OperatorHelmChartContainer(DockerImageNames.K3s.DEFAULT,
                    "values/stateful-set-values.yaml",
                    CHART_NAME).withLocalImages();

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void withStatefulSet_RollingUpgrade() throws Exception {
        final var client = container.getKubernetesClient();
        final var namespace = "default";

        container.upgradeLocalChart(CHART_NAME, "/values/update-stateful-set-values.yaml");

        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Updating");
        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Running");

        final var hiveMQResource = K8sUtil.getHiveMQCluster(client, namespace, CHART_NAME);
        assertThat(hiveMQResource).isNotNull();
        assertThat(hiveMQResource.get().get("status").toString()).contains("Running");

        await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
            final var statefulSet = client.apps().statefulSets().inNamespace(namespace).withName(CHART_NAME).get();
            assertThat(statefulSet).isNotNull();
            assertThat(statefulSet.getStatus().getAvailableReplicas()).isEqualTo(2);
            assertThat(statefulSet.getStatus().getReadyReplicas()).isEqualTo(2);
            assertThat(statefulSet.getStatus().getUpdatedReplicas()).isEqualTo(2);
        });
    }
}
