package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.testcontainer.AdditionalK3sCommands;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import com.hivemq.helmcharts.util.PodUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("Services")
@Tag("ClusterDomainName")
@AdditionalK3sCommands(commands = {"--cluster-domain=hivemq.com"})
class CustomClusterDomainNameIT extends AbstractHelmChartIT {

    private static final @NotNull String MQTT_SERVICE_NAME = "hivemq-test-hivemq-platform-mqtt-1884";
    private static final int MQTT_SERVICE_PORT = 1884;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenK8sHasCustomClusterDomainName_thenServicesAreRunning() throws Exception {
        installPlatformChartAndWaitToBeRunning("/files/mqtt-values.yaml");
        K8sUtil.assertMqttService(client, platformNamespace, MQTT_SERVICE_NAME);
        MqttUtil.assertMessages(client, platformNamespace, MQTT_SERVICE_NAME, MQTT_SERVICE_PORT);

        // assert cluster domain name is set as expected `hivemq.com` and does not contain default `cluster.local`
        client.pods().inNamespace(platformNamespace).list().getItems().forEach(pod -> {
            final var podName = pod.getMetadata().getName();
            final var execResult =
                    PodUtil.execute(client, platformNamespace, podName, "hivemq", "cat", "/etc/resolv.conf");
            try {
                assertThat(execResult.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(execResult.getOutput()).isNotNull();
                assertThat(execResult.getOutput().split("[\\s\\n]+")) //
                        .contains(platformNamespace + ".svc.hivemq.com", "svc.hivemq.com", "hivemq.com")
                        .doesNotContain("cluster.local");
                assertThat(execResult.getError()).isNull();
            } finally {
                execResult.close();
            }
        });
    }
}
