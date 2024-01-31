package com.hivemq.helmcharts;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("RollingUpgrades")
@Testcontainers
public class StatefulSetRollingUpgradeIT {
    private static final @NotNull String CHART_NAME = "local-hivemq";
    @Container
    private final @NotNull OperatorHelmChartContainer
            container = new OperatorHelmChartContainer(DockerImageNames.K3s.V1_27,
            "k3s.dockerfile",
            "values/stateful-set-values.yaml",
            CHART_NAME)
            .withLocalImages();

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void withStatefulSet_RollingUpgrade() throws Exception {
        final var client = container.getKubernetesClient();
        final var namespace = "default";

        container.upgradeLocalChart(CHART_NAME, "/values/update-stateful-set-values.yaml");

        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Updating");
        K8sUtil.waitForHiveMQClusterState(client, namespace, CHART_NAME, "Running");

        final var hiveMQResource = K8sUtil.getHiveMQCluster(client, namespace, CHART_NAME);
        assertNotNull(hiveMQResource);
        assertThat(hiveMQResource.get().get("status").toString()).contains("Running");

        await().atMost(1, TimeUnit.MINUTES).untilAsserted(()->{
            final var statefulSet = client.apps().statefulSets().inNamespace(namespace).withName(CHART_NAME).get();
            assertNotNull(statefulSet);
            assertEquals(2, statefulSet.getStatus().getAvailableReplicas());
            assertEquals(2, statefulSet.getStatus().getReadyReplicas());
            assertEquals(2, statefulSet.getStatus().getUpdatedReplicas());
        });

    }
}
