package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_MINUTES;

/**
 * Tests the operator permissions when deleting the namespace.
 */
@Tag("Namespace")
class HelmRemoveNamespaceIT extends AbstractHelmChartIT {

    @Override
    protected boolean uninstallPlatformChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withSingleOperator_hivemqRunningThenDelete() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/platform-values.yaml");

        final var platform = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertThat(platform).isNotNull();
        final var namespaceDeletedFuture = client.namespaces()
                .withName(platformNamespace)
                .informOnCondition(namespaces -> namespaces.stream()
                        .noneMatch(n -> Objects.equals(n.getMetadata().getName(), platformNamespace)));
        assertThat(platform.delete()).isNotEmpty();
        assertThat(client.namespaces().withName(platformNamespace).delete()).isNotEmpty();
        await().atMost(TWO_MINUTES).until(namespaceDeletedFuture::isDone);
    }
}
