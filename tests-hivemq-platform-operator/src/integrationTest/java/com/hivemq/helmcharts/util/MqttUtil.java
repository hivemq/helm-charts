package com.hivemq.helmcharts.util;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unused")
public class MqttUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(MqttUtil.class);

    private MqttUtil() {
    }

    public static void execute(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName,
            final int mqttServicePort,
            final @NotNull MqttPublishRunnable runnable) {
        try (final var portForward = K8sUtil.getPortForward(client, namespace, mqttServiceName, mqttServicePort)) {
            final var publishClient = getBlockingClient(portForward, "PublishClient");

            connect(publishClient);
            runnable.run(publishClient);
            disconnect(publishClient);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void execute(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName,
            final int mqttServicePort,
            final @NotNull MqttSubscribeRunnable runnable) {
        try (final var portForward = K8sUtil.getPortForward(client, namespace, mqttServiceName, mqttServicePort)) {
            final var subscribeClient = getBlockingClient(portForward, "SubscribeClient");

            connect(subscribeClient);
            try (final var publishes = subscribeClient.publishes(MqttGlobalPublishFilter.ALL)) {
                runnable.run(subscribeClient, publishes);
            }
            disconnect(subscribeClient);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void execute(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName,
            final int mqttServicePort,
            final @NotNull Function<LocalPortForward, Mqtt5BlockingClient> publishClientFunction,
            final @NotNull Function<LocalPortForward, Mqtt5BlockingClient> subscribeClientFunction,
            final @NotNull MqttPublishSubscribeRunnable runnable) {
        try (final var portForward = K8sUtil.getPortForward(client, namespace, mqttServiceName, mqttServicePort)) {
            final var publishClient = publishClientFunction.apply(portForward);
            final var subscribeClient = subscribeClientFunction.apply(portForward);

            connect(publishClient);
            connect(subscribeClient);
            try (final var publishes = subscribeClient.publishes(MqttGlobalPublishFilter.ALL)) {
                runnable.run(publishClient, subscribeClient, publishes);
            }
            disconnect(subscribeClient);
            disconnect(publishClient);
        } catch (final Exception e) {
            throw new AssertionError(e);
        }

    }

    public static void execute(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName,
            final int mqttServicePort,
            final @NotNull MqttPublishSubscribeRunnable runnable) {
        execute(client,
                namespace,
                mqttServiceName,
                mqttServicePort,
                portForward -> getBlockingClient(portForward, "PublishClient"),
                portForward -> getBlockingClient(portForward, "SubscribeClient"),
                runnable);
    }

    public static @NotNull Mqtt5BlockingClient getBlockingClient(
            final @NotNull String hostname, final int port, final @NotNull String identifier) {
        return getBlockingClient(hostname, port, identifier, Function.identity());
    }

    public static @NotNull Mqtt5BlockingClient getBlockingClient(
            final @NotNull LocalPortForward portForward, final @NotNull String identifier) {
        return getBlockingClient(portForward, identifier, Function.identity());
    }

    public static @NotNull Mqtt5BlockingClient getBlockingClient(
            final @NotNull LocalPortForward portForward,
            final @NotNull String identifier,
            final @NotNull Function<Mqtt5ClientBuilder, Mqtt5ClientBuilder> modifier) {
        return getBlockingClient(portForward.getLocalAddress().getHostName(),
                portForward.getLocalPort(),
                identifier,
                modifier);
    }

    public static @NotNull Mqtt5BlockingClient getBlockingClient(
            final @NotNull String hostname,
            final int port,
            final @NotNull String identifier,
            final @NotNull Function<Mqtt5ClientBuilder, Mqtt5ClientBuilder> modifier) {
        final var clientBuilder =
                MqttClient.builder().identifier(identifier).serverHost(hostname).serverPort(port).useMqttVersion5();
        return modifier.apply(clientBuilder).buildBlocking();
    }

    public static void connect(final @NotNull Mqtt5BlockingClient client) {
        final var connAck = client.connect();
        assertThat(connAck.getReasonCode().isError()).isFalse();
        assertThat(client.getConfig().getState().isConnected()).isTrue();
    }

    public static void disconnect(final @NotNull Mqtt5BlockingClient client) {
        try {
            client.disconnect();
        } catch (final MqttClientStateException e) {
            LOG.warn("MQTT client could not be disconnected: {}", e.getMessage());
        }
        assertThat(client.getConfig().getState().isConnected()).isFalse();
    }

    public static @NotNull MqttPublishSubscribeRunnable withDefaultPublishSubscribeRunnable(){
        return (publishClient, subscribeClient, publishes) -> {
            final var topic = "topic01";
            final var payload = "test".getBytes(StandardCharsets.UTF_8);
            subscribeClient.subscribeWith().topicFilter(topic).send();
            LOG.info("Client subscribed");

            publishClient.publishWith().topic(topic).payload(payload).send();
            LOG.info("Client published");

            final var publish = publishes.receive(1, TimeUnit.MINUTES);
            assertThat(publish).isPresent();
            assertThat(publish.get().getPayloadAsBytes()).isEqualTo(payload);
            LOG.info("Client received");
        };
    }

    public interface MqttPublishRunnable {
        void run(final @NotNull Mqtt5BlockingClient publishClient) throws Exception;
    }

    public interface MqttSubscribeRunnable {
        void run(
                final @NotNull Mqtt5BlockingClient subscribeClient,
                final @NotNull Mqtt5BlockingClient.Mqtt5Publishes publishes) throws Exception;
    }

    public interface MqttPublishSubscribeRunnable {
        void run(
                final @NotNull Mqtt5BlockingClient publishClient,
                final @NotNull Mqtt5BlockingClient subscribeClient,
                final @NotNull Mqtt5BlockingClient.Mqtt5Publishes publishes) throws Exception;
    }
}
