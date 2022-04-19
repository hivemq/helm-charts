package com.hivemq.helmcharts.util;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.crd.hivemq.HiveMQInfo;
import com.hivemq.openapi.HivemqClusterStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUtils {
    /**
     * Return a count-down latch that will be decreased when the hivemq cluster is running.
     * The helm chart makes sure it is installed, and the operator makes sure the state is
     * update to running, we listen for this status change.
     *
     * @param kubeConfigYaml k3s yaml configuration for the container that is running and waiting for the Kubernetes artifacts to be ready
     */
    public static CountDownLatch getWaitForClusterToBeReadyLatch(final @NotNull String kubeConfigYaml) {
        Config config = Config.fromKubeconfig(kubeConfigYaml);
        DefaultKubernetesClient client = new DefaultKubernetesClient(config);
        final CountDownLatch closeLatch = new CountDownLatch(1);
        client.customResources(HiveMQInfo.class).watch(new Watcher<>() {
            @Override
            public void eventReceived(@NotNull Action action, @NotNull HiveMQInfo resource) {
                if (resource.getStatus() != null
                        && resource.getStatus().getState() != null
                        && resource.getStatus().getState() == HivemqClusterStatus.State.RUNNING) {
                    closeLatch.countDown();
                }
            }

            @Override
            public void onClose(@NotNull WatcherException cause) {
                System.out.println("onClose");
            }
        });
        return closeLatch;
    }
    public static void sendTestMessage(int mappedPort) throws InterruptedException {
        Mqtt5BlockingClient client = Mqtt5Client.builder()
                .automaticReconnectWithDefaultConfig()
                .serverPort(mappedPort)
                .buildBlocking();
        client.connect();

        var publishes = client.publishes(MqttGlobalPublishFilter.ALL);
        client.subscribeWith().topicFilter("test").send();
        client.publishWith()
                .topic("test")
                .payload("Sending Message".getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE).send();
        Mqtt5Publish receivedMessage = publishes.receive();
        assertTrue(receivedMessage.getPayload().isPresent());
        assertEquals("Sending Message", StandardCharsets.UTF_8.decode(receivedMessage
                .getPayload().get().asReadOnlyBuffer()).toString());
    }

}
