package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.extensions.AppendingBarPublishInboundInterceptorExtensionMain;
import com.hivemq.helmcharts.extensions.AppendingBazPublishInboundInterceptorExtensionMain;
import com.hivemq.helmcharts.extensions.AppendingFooPublishInboundInterceptorExtensionMain;
import com.hivemq.helmcharts.util.HiveMQExtension;
import com.hivemq.helmcharts.util.MqttUtil;
import com.hivemq.helmcharts.util.NginxUtil;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("Extensions")
class HelmExtensionPriorityIT extends AbstractHelmChartIT {

    private static final @NotNull String MQTT_SERVICE_NAME = "hivemq-test-hivemq-platform-mqtt-1884";
    private static final int MQTT_SERVICE_PORT = 1884;

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmExtensionPriorityIT.class);

    @TempDir
    private @NotNull Path tmp;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformChart_whenExtensionPriorityIsConfigured_thenInterceptorsAreExecutedInTheExpectedOrder()
            throws Exception {
        final var appendingFooExtension = HiveMQExtension.createHiveMQExtensionZip(tmp,
                "hivemq-appending-foo-extension",
                "HiveMQ Appending Foo Extension",
                "1.0.0",
                AppendingFooPublishInboundInterceptorExtensionMain.class);
        final var appendingBarExtension = HiveMQExtension.createHiveMQExtensionZip(tmp,
                "hivemq-appending-bar-extension",
                "HiveMQ Appending Bar Extension",
                "1.0.0",
                AppendingBarPublishInboundInterceptorExtensionMain.class);
        final var appendingBazExtension = HiveMQExtension.createHiveMQExtensionZip(tmp,
                "hivemq-appending-baz-extension",
                "HiveMQ Appending Baz Extension",
                "1.0.0",
                AppendingBazPublishInboundInterceptorExtensionMain.class);
        NginxUtil.deployNginx(client,
                namespace,
                helmChartContainer,
                List.of(appendingFooExtension, appendingBarExtension, appendingBazExtension),
                false);
        final var fooExtensionStartedFuture = helmChartContainer.getLogWaiter()
                .waitFor("test-hivemq-platform-0",
                        ".*Extension \"HiveMQ Appending Foo Extension\" version 1.0.0 started successfully.");
        final var barExtensionStartedFuture = helmChartContainer.getLogWaiter()
                .waitFor("test-hivemq-platform-0",
                        ".*Extension \"HiveMQ Appending Bar Extension\" version 1.0.0 started successfully.");
        final var bazExtensionStartedFuture = helmChartContainer.getLogWaiter()
                .waitFor("test-hivemq-platform-0",
                        ".*Extension \"HiveMQ Appending Baz Extension\" version 1.0.0 started successfully.");

        installChartsAndWaitForPlatformRunning("/files/extension-priority-test-values.yaml");

        await().atMost(1, TimeUnit.MINUTES).until(fooExtensionStartedFuture::isDone);
        await().atMost(1, TimeUnit.MINUTES).until(barExtensionStartedFuture::isDone);
        await().atMost(1, TimeUnit.MINUTES).until(bazExtensionStartedFuture::isDone);
        await().untilAsserted(() -> assertThat(client.pods()
                .inNamespace(namespace)
                .withName("test-hivemq-platform-0")
                .get()) //
                .isNotNull() //
                .satisfies(pod -> {
                    assertThatExtensionXmlContains(pod,
                            "hivemq-appending-foo-extension",
                            "<priority>3000</priority>",
                            "<start-priority>1000</start-priority>");
                    assertThatExtensionXmlContains(pod,
                            "hivemq-appending-bar-extension",
                            "<priority>2000</priority>",
                            "<start-priority>2000</start-priority>");
                    assertThatExtensionXmlContains(pod,
                            "hivemq-appending-baz-extension",
                            "<priority>1000</priority>",
                            "<start-priority>3000</start-priority>");
                }));

        await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
            final var services = client.services().inNamespace(namespace).list().getItems();
            assertThat(services).isNotEmpty()
                    .filteredOn(service -> MQTT_SERVICE_NAME.equals(service.getMetadata().getName()))
                    .extracting(Service::getSpec)
                    .extracting(ServiceSpec::getType)
                    .contains("ClusterIP");
        });

        MqttUtil.execute(client,
                namespace,
                MQTT_SERVICE_NAME,
                MQTT_SERVICE_PORT,
                (publishClient, subscribeClient, publishes) -> {
                    subscribeClient.subscribeWith().topicFilter("topic").send();
                    LOG.info("Client subscribed");

                    publishClient.publishWith().topic("topic").payload("test".getBytes()).send();
                    LOG.info("Client published");

                    final var publish = publishes.receive(1, TimeUnit.MINUTES);
                    assertThat(publish).isPresent();
                    assertThat(new String(publish.get().getPayloadAsBytes())).isEqualTo("test-foo-bar-baz");
                    LOG.info("Message received by Client");
                });
    }

    private void assertThatExtensionXmlContains(
            final @NotNull Pod pod, final @NotNull String extensionId, final @NotNull String... values) {
        try {
            final var extensionXml = tmp.resolve(extensionId).resolve("hivemq-extension.xml");
            Files.createDirectories(extensionXml.getParent());
            Files.deleteIfExists(extensionXml);
            client.pods()
                    .inNamespace(namespace)
                    .withName(pod.getMetadata().getName())
                    .file(String.format("/opt/hivemq/extensions/%s/hivemq-extension.xml", extensionId))
                    .copy(extensionXml);
            final var logbackXml = Files.readString(extensionXml);
            for (final var value : values) {
                assertThat(logbackXml).contains(value);
            }
        } catch (final IOException e) {
            throw new AssertionError(String.format("Could not copy hivemq-extension.xml file for extension %s from pod",
                    extensionId), e);
        }
    }
}
