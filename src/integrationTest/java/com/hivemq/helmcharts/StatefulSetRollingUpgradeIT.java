package com.hivemq.helmcharts;

import com.hivemq.crd.hivemq.HiveMQInfo;
import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.OperatorHelmChartContainer;
import com.hivemq.openapi.HivemqClusterStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void withStatefulSet_RollingUpgrade() throws Exception {
        final var client = container.getKubernetesClient();
        container.upgradeLocalChart(CHART_NAME, "/values/update-stateful-set-values.yaml");

        container.waitForClusterState(HivemqClusterStatus.State.UPDATING);
        container.waitForClusterState(HivemqClusterStatus.State.RUNNING);

        final var hiveMQResource = client.resources(HiveMQInfo.class).inNamespace("default").withName(CHART_NAME).get();
        assertNotNull(hiveMQResource);
        assertEquals(hiveMQResource.getStatus().getState(), HivemqClusterStatus.State.RUNNING);

        await().atMost(1, TimeUnit.MINUTES).untilAsserted(()->{
            final var statefulSet = client.apps().statefulSets().inNamespace("default").withName(CHART_NAME).get();
            assertNotNull(statefulSet);
            assertEquals(2, statefulSet.getStatus().getAvailableReplicas());
            assertEquals(2, statefulSet.getStatus().getReadyReplicas());
            assertEquals(2, statefulSet.getStatus().getUpdatedReplicas());
        });

    }
}
