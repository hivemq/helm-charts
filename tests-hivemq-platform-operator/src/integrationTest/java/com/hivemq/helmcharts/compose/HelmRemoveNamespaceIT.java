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

/**
 * Tests the operator permissions when deleting the namespace.
 */
@Tag("Namespace")
class HelmRemoveNamespaceIT extends AbstractHelmChartIT {

    @Override
    protected boolean cleanupPlatformChart() {
        return false;
    }

    @Override
    protected boolean cleanupNamespace() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withSingleOperator_hivemqRunningThenDelete() throws Exception {
        installChartsAndWaitForPlatformRunning("/files/platform-test-values.yaml");

        final var platform = K8sUtil.getHiveMQPlatform(client, namespace, PLATFORM_RELEASE_NAME);
        assertThat(platform).isNotNull();
        final var namespaceDeletedFuture = client.namespaces()
                .withName(namespace)
                .informOnCondition(namespaces -> namespaces.stream()
                        .noneMatch(n -> Objects.equals(n.getMetadata().getName(), namespace)));
        assertThat(platform.delete()).isNotEmpty();
        assertThat(client.namespaces().withName(namespace).delete()).isNotEmpty();
        await().atMost(2, TimeUnit.MINUTES).until(namespaceDeletedFuture::isDone);
    }
}
