package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_MINUTES;

@Disabled
class HelmInstallRemoteImagesIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withRemoteImages_hivemqRunning() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/platform-values.yaml");

        await().atMost(TWO_MINUTES).untilAsserted(() -> {
            // check the StatefulSet spec contains the same default "ghcr" imagePullSecrets as per the default created for the operator
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull()
                    .extracting(StatefulSet::getSpec)
                    .satisfies(statefulSetSpec -> assertThat(statefulSetSpec.getTemplate().getSpec()) //
                            .satisfies(podSpec -> assertThat(podSpec.getImagePullSecrets()) //
                                    .hasSize(1) //
                                    .containsExactly(new LocalObjectReference("ghcr"))));
        });
    }
}
