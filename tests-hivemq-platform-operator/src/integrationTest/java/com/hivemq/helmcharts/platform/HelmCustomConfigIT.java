package com.hivemq.helmcharts.platform;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmCustomConfigIT extends AbstractHelmChartIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomYml_hivemqRunning() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning();
        installPlatformChartAndWaitToBeRunning("--set-file",
                "config.overrideStatefulSet=/files/stateful-set-spec.yaml");

        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var foundContainer = statefulSet.getSpec()
                    .getTemplate()
                    .getSpec()
                    .getContainers()
                    .stream()
                    .filter(c -> c.getName().equalsIgnoreCase("hivemq"))
                    .findFirst();
            assertThat(foundContainer).isPresent();
            assertThat(foundContainer.get() //
                    .getPorts() //
                    .stream() //
                    .filter(p -> p.getName().startsWith("mqtt"))) //
                    .anyMatch(p -> p.getContainerPort().equals(1884));
        });
    }
}
