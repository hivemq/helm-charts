package com.hivemq.helmcharts;

import com.hivemq.helmcharts.util.PodUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public abstract class AbstractHelmSecurityContextIT extends AbstractHelmChartIT {

    @SuppressWarnings("SameParameterValue")
    protected static void assertUidAndGid(
            final @NotNull String namespace,
            final @NotNull Map<String, String> labels,
            final @NotNull String containerName,
            final int expectedUid,
            final int expectedGid) {
        client.pods().inNamespace(namespace).withLabels(labels).list().getItems().forEach(pod -> {
            final var execResult = PodUtil.execute(client,
                    namespace,
                    pod.getMetadata().getName(),
                    containerName,
                    "sh",
                    "-c",
                    "stat -c \"%u:%g\" /proc/1");
            try {
                assertThat(execResult.await(2, TimeUnit.MINUTES)).isTrue();
                assertThat(execResult.getOutput()).isEqualTo(expectedUid + ":" + expectedGid);
                assertThat(execResult.getError()).isNull();
            } catch (final Exception e) {
                fail("Could not retrieve UID and GID from pod '%s': %s", pod.getMetadata().getName(), e);
            } finally {
                execResult.close();
            }
        });
    }

    protected @NotNull Stream<Arguments> chartValues() {
        return Stream.of(arguments(new ChartValues(new Values(0, 0, operatorChartRootUserValuesFile()),
                        new Values(0, 0, platformChartRootUserValuesFile()))),
                // Default Operator non-root UID is 185 and GID is 0
                arguments(new ChartValues(new Values(185, 0, operatorChartNonRootUserValuesFile()),
                        new Values(10000, // Default Platform non-root UID is 1000 and GID is 0
                                0, platformChartNonRootUserValuesFile()))));
    }

    protected record ChartValues(@NotNull Values operator, @NotNull Values platform) {
        @Override
        public String toString() {
            return String.format("Operator values: %s and Platform values: %s", operator, platform);
        }
    }

    protected record Values(int uid, int gid, @NotNull String valuesFile) {
        @Override
        public String toString() {
            return String.format("(UID=%s, GID=%s, valuesFile='%s')", uid, gid, valuesFile);
        }
    }

    /**
     * Override with a custom resource values file to use for as a root user for the Operator chart.
     */
    protected abstract @NotNull String operatorChartRootUserValuesFile();

    /**
     * Override with a custom resource values file to use for as a root user for the Platform chart.
     */
    protected abstract @NotNull String platformChartRootUserValuesFile();

    /**
     * Override with a custom resource values file to use for as a non-root user for the Operator chart.
     */
    protected abstract @NotNull String operatorChartNonRootUserValuesFile();

    /**
     * Override with a custom resource values file to use for as a non-root user for the Platform chart.
     */
    protected abstract @NotNull String platformChartNonRootUserValuesFile();
}
