package com.hivemq.helmcharts.platform;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

class HelmCustomConfigMapIT extends AbstractHelmChartIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingConfigMap_customResourceCreated() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning();
        final var configMap = K8sUtil.createConfigMap(client, platformNamespace, "hivemq-config-map.yml");
        final var configMapName = configMap.getMetadata().getName();

        installPlatformChartAndWaitToBeRunning("--set", "config.create=false", "--set", "config.name=" + configMapName);

        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var hivemqCustomResource =
                    K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME).get();
            assertThat(hivemqCustomResource.getAdditionalProperties().get("spec")).isNotNull()
                    .asString()
                    .containsIgnoringCase("configMapName=" + configMapName);
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            assertThat(K8sUtil.getHiveMQContainer(statefulSet.getSpec()).getVolumeMounts()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("broker-configuration") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/conf-k8s/"));

            assertThat(statefulSet.getSpec().getTemplate().getSpec().getVolumes()) //
                    .isNotNull() //
                    .anyMatch(volume -> volume.getName().equals("broker-configuration") &&
                            volume.getConfigMap().getName().equals(configMapName));
        });
    }
}
