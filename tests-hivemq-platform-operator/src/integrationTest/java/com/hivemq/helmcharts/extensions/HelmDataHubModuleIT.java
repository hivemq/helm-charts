package com.hivemq.helmcharts.extensions;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import com.hivemq.helmcharts.util.MqttUtil;
import io.restassured.http.ContentType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests the HiveMQ Platform with a Data Hub module that validates JSON messages and applies a transformation script.
 * <p>
 * The transformation script runs in the embedded JavaScript engine, which loads a native library and tears it down when
 * the HiveMQ Platform stops, so the test also asserts that a restart produces no dynamic linker error.
 */
@Tag("custom-platform-image")
class HelmDataHubModuleIT extends AbstractHelmChartIT {

    private static final int MQTT_SERVICE_PORT = 1883;
    private static final int REST_API_SERVICE_PORT = 8890;
    private static final @NotNull String VALUES_FILE = "/files/data-hub-values.yaml";

    // https://github.com/hivemq/hivemq-hello-world-datahub-module
    private static final @NotNull String @NotNull [] MODULE_FILES = {
            "index.json",
            "variables.json",
            "data-policy.json.vm",
            "schema.json",
            "schema-definition.json",
            "script.json",
            "script.js",};

    private final @NotNull String mqttServiceName =
            "hivemq-%s-mqtt-%s".formatted(platformReleaseName, MQTT_SERVICE_PORT);
    private final @NotNull String restApiServiceName =
            "hivemq-%s-rest-%s".formatted(platformReleaseName, REST_API_SERVICE_PORT);

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void platformChart_whenDataHubModuleIsApplied_thenTransformsMessagesAndRestartsWithoutLinkerError()
            throws Exception {
        installPlatformChartAndWaitToBeRunning(platformChartCommands());

        // the trial enables the custom modules of Data Hub
        startDataHubTrial();

        final var moduleBase64 = Base64.getEncoder().encodeToString(createModuleZip());
        final var jsonPayload = """
                {"module":"%s","moduleConfiguration":{"topicFilter":"test/#","dropFailedMessages":true,"addsTimestampToPayload":true}}""".formatted(
                moduleBase64).trim();
        final var statusCode = createDataHubModule(jsonPayload);
        assumeThat(statusCode) //
                .as("Data Hub modules endpoint not available, skipping test") //
                .isNotEqualTo(404);
        assertThat(statusCode).as("Create module failed with status %s", statusCode).isBetween(200, 299);

        final var successLog = waitForPlatformLog(".*The client PublishClient sent a valid JSON message.*");
        final var failureLog = waitForPlatformLog(".*The client PublishClient sent an invalid message.*");

        MqttUtil.execute(client,
                platformNamespace,
                mqttServiceName,
                MQTT_SERVICE_PORT,
                (publishClient, subscribeClient, publishes) -> {
                    subscribeClient.subscribeWith().topicFilter("test/#").send();

                    publishClient.publishWith().topic("test/data").payload("""
                            {"hello":"world"}""".getBytes(StandardCharsets.UTF_8)).send();

                    // the transformation script adds a timestamp field
                    final var validMessage = publishes.receive(1, TimeUnit.MINUTES);
                    assertThat(validMessage).isPresent();
                    final var payload = new String(validMessage.get().getPayloadAsBytes(), StandardCharsets.UTF_8);
                    assertThat(payload).matches("""
                            \\{"hello":"world","timestamp":"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"}""");

                    publishClient.publishWith()
                            .topic("test/data")
                            .payload("not json".getBytes(StandardCharsets.UTF_8))
                            .send();

                    final var invalidMessage = publishes.receive(10, TimeUnit.SECONDS);
                    assertThat(invalidMessage).isEmpty();
                });

        assertThat(successLog.get(1, TimeUnit.MINUTES)).isNotNull();
        assertThat(failureLog.get(1, TimeUnit.MINUTES)).isNotNull();

        // a native library that resolves its symbols through the JVM instead of its own dependencies fails when the
        // JavaScript engine is torn down, which only happens when the HiveMQ Platform stops
        final var linkerErrorLog = waitForPlatformLog(".*(symbol lookup error|undefined symbol).*");
        restartPlatformPod();
        assertThat(linkerErrorLog).isNotDone();
    }

    /**
     * The native library of the script engine resolves its math symbols through the JVM. HotSpot links the math library
     * and loads it into the global symbol scope, OpenJ9 does not, so the library needs it preloaded there.
     */
    // TODO: Remove once Javet adds the missing compiler flag
    private @NotNull String[] platformChartCommands() {
        if (CUSTOM_PLATFORM_IMAGE_VARIANT.startsWith("semeru")) {
            return new String[]{
                    "-f",
                    VALUES_FILE,
                    "--set",
                    "nodes.env[0].name=LD_PRELOAD",
                    "--set",
                    "nodes.env[0].value=libm.so.6"};
        }
        return new String[]{"-f", VALUES_FILE};
    }

    private void startDataHubTrial() {
        await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
            try (final var forwarded = K8sUtil.getPortForward(client,
                    platformNamespace,
                    restApiServiceName,
                    REST_API_SERVICE_PORT)) {
                final var statusCode = given().when()
                        .post("http://localhost:%s/api/v1/data-hub/management/start-trial".formatted(forwarded.getLocalPort()))
                        .then()
                        .log()
                        .ifError()
                        .extract()
                        .statusCode();
                assertThat(statusCode).as("Start trial failed with status %s", statusCode).isBetween(200, 299);
            }
        });
    }

    private int createDataHubModule(final @NotNull String jsonPayload) throws Exception {
        try (final var forwarded = K8sUtil.getPortForward(client,
                platformNamespace,
                restApiServiceName,
                REST_API_SERVICE_PORT)) {
            return given().contentType(ContentType.JSON)
                    .body(jsonPayload)
                    .when()
                    .post("http://localhost:%s/api/v1/data-hub/modules/instances".formatted(forwarded.getLocalPort()))
                    .then()
                    .log()
                    .ifError()
                    .extract()
                    .statusCode();
        }
    }

    private byte @NotNull [] createModuleZip() throws Exception {
        final var byteArrayOutputStream = new ByteArrayOutputStream();
        try (final var zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (final var fileName : MODULE_FILES) {
                addZipEntry(zipOutputStream, "hello-world/" + fileName, readResourceFile("datahub/" + fileName));
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    private void restartPlatformPod() {
        final var podName = platformReleaseName + "-0";
        final var podResource = client.pods().inNamespace(platformNamespace).withName(podName);
        final var podUid = podResource.get().getMetadata().getUid();
        podResource.delete();
        K8sUtil.waitForPodStateRunning(client, platformNamespace, podName, podUid);
        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, platformReleaseName);
    }

    private static void addZipEntry(
            final @NotNull ZipOutputStream zipOutputStream,
            final @NotNull String entryName,
            final @NotNull String content) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }
}
