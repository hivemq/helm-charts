package com.hivemq.helmcharts.util;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient.Mqtt5Publishes;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class MqttUtil {

    public static final int DEFAULT_TOPIC_COUNT = 30;

    // generate strings from a to z
    private static final int RANDOM_STRING_LEFT_LIMIT = 97;
    private static final int RANDOM_STRING_RIGHT_LIMIT = 122;
    private static final int RANDOM_STRING_LENGTH = 15;

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(MqttUtil.class);

    private MqttUtil() {
    }

    public static void assertMessages(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName,
            final int mqttServicePort) {
        assertMessages(client, namespace, mqttServiceName, mqttServicePort, Function.identity());
    }

    public static void assertMessages(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName,
            final int mqttServicePort,
            final @NotNull Function<Mqtt5ClientBuilder, Mqtt5ClientBuilder> mqttClientModifier) {
        final var random = new Random();
        execute(client,
                namespace,
                mqttServiceName,
                mqttServicePort,
                portForward -> getBlockingClient(portForward, "PublishClient", mqttClientModifier),
                portForward -> getBlockingClient(portForward, "SubscribeClient", mqttClientModifier),
                (publishClient, subscribeClient, publishes) -> {
                    IntStream.range(0, DEFAULT_TOPIC_COUNT).parallel().forEach(i -> {
                        final var topic = random.ints(RANDOM_STRING_LEFT_LIMIT, RANDOM_STRING_RIGHT_LIMIT + 1)
                                .limit(RANDOM_STRING_LENGTH)
                                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                .toString();
                        final var payload = "test".getBytes(StandardCharsets.UTF_8);
                        subscribeClient.subscribeWith().topicFilter(topic).send();

                        final var publishResult = publishClient.publishWith().topic(topic).payload(payload).send();
                        assertThat(publishResult.getError()).isEmpty();

                        final Optional<Mqtt5Publish> publish;
                        try {
                            publish = publishes.receive(1, TimeUnit.MINUTES);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                        assertThat(publish).isPresent();
                        assertThat(publish.get().getPayloadAsBytes()).isEqualTo(payload);
                    });
                });
    }

    public static void execute(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String mqttServiceName,
            final int mqttServicePort,
            final @NotNull MqttPublishSubscribeRunnable runnable) {
        // forward the port from the service
        try (final var portForward = K8sUtil.getPortForward(client, namespace, mqttServiceName, mqttServicePort)) {
            final var publishClient = getBlockingClient(portForward, "PublishClient");
            final var subscribeClient = getBlockingClient(portForward, "SubscribeClient");

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
            final @NotNull Function<LocalPortForward, Mqtt5BlockingClient> publishClientModifier,
            final @NotNull Function<LocalPortForward, Mqtt5BlockingClient> subscribeClientModifier,
            final @NotNull MqttPublishSubscribeRunnable runnable) {
        try (final var portForward = K8sUtil.getPortForward(client, namespace, mqttServiceName, mqttServicePort)) {
            final var publishClient = publishClientModifier.apply(portForward);
            final var subscribeClient = subscribeClientModifier.apply(portForward);

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

    public static @NotNull Mqtt5BlockingClient getBlockingClient(
            final @NotNull LocalPortForward portForward, final @NotNull String identifier) {
        return getBlockingClient(portForward, identifier, Function.identity());
    }

    public static @NotNull Mqtt5BlockingClient getBlockingClient(
            final @NotNull String hostname, final int port, final @NotNull String identifier) {
        return getBlockingClient(hostname, port, identifier, Function.identity());
    }

    public static @NotNull Mqtt5BlockingClient getBlockingClient(
            final @NotNull LocalPortForward portForward,
            final @NotNull String identifier,
            final @NotNull Function<Mqtt5ClientBuilder, Mqtt5ClientBuilder> modifier) {
        final var clientBuilder =
                MqttClient.builder().identifier(identifier).serverPort(portForward.getLocalPort()).useMqttVersion5();
        return modifier.apply(clientBuilder).buildBlocking();
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

    public interface MqttPublishSubscribeRunnable {

        void run(
                final @NotNull Mqtt5BlockingClient publishClient,
                final @NotNull Mqtt5BlockingClient subscribeClient,
                final @NotNull Mqtt5Publishes publishes) throws Exception;
    }
}
