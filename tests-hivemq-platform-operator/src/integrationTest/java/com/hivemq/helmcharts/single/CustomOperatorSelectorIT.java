package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("OperatorSelector")
class CustomOperatorSelectorIT extends AbstractHelmChartIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenK8sHasCustomClusterDomainName_thenServicesAreRunning() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning("--set", "selector=operator-group=alpha");

        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set",
                "nodes.labels.operator-group=alpha");
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME + "-beta",
                "--namespace",
                platformNamespace,
                "--set",
                "nodes.replicaCount=1",
                "--set",
                "nodes.labels.operator-group=beta");

        // assert that both custom resources are present, but only one StatefulSet
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME).get()).isNotNull();
        assertThat(K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME + "-beta")
                .get()).isNotNull();
        assertThat(client.apps().statefulSets().inNamespace(platformNamespace).list().getItems()).singleElement()
                .satisfies(statefulSet -> assertThat(statefulSet.getMetadata().getName()) //
                        .isEqualTo(PLATFORM_RELEASE_NAME));
    }
}
