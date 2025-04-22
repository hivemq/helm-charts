package com.hivemq.helmcharts.platform;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.Pod;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class UpdateLogLevelIT extends AbstractHelmChartIT {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(UpdateLogLevelIT.class);

    @TempDir
    private @NotNull Path tempDir;

    private @NotNull Path logbackFile;

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @BeforeEach
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void setUp() {
        logbackFile = tempDir.resolve("logback.xml");
    }

    @ParameterizedTest(name = "{index} withNamespaces: {0}")
    @ValueSource(booleans = {true, false})
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void whenLogLevelIsChanged_thenLogbackXmlIsUpdated(final boolean withNamespaces) throws Exception {
        if (withNamespaces) {
            installPlatformOperatorChartAndWaitToBeRunning("--set", "namespaces=%s".formatted(platformNamespace));
        } else {
            installPlatformOperatorChartAndWaitToBeRunning();
        }
        installPlatformChartAndWaitToBeRunning("/files/platform-values.yaml");
        final var hivemqCustomResource = K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME);

        await().untilAsserted(() -> {
            LOG.info("Assert original logback.xml in pods");
            assertThat(client.pods()
                    .inNamespace(platformNamespace)
                    .list()
                    .getItems()
                    .stream()
                    .filter(pod -> pod.getMetadata().getName().startsWith(PLATFORM_RELEASE_NAME + "-"))) //
                    .allSatisfy(pod -> assertThatLogbackXmlContains(pod, "<root level=\"${HIVEMQ_LOG_LEVEL:-INFO}\">"));
        });

        LOG.info("Trigger update of log level");
        helmChartContainer.upgradePlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.logLevel=WARN",
                "--namespace",
                platformNamespace);

        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("SET_LOG_LEVEL"),
                1,
                TimeUnit.MINUTES);
        hivemqCustomResource.waitUntilCondition(K8sUtil.getCustomResourceStateCondition("RUNNING"),
                3,
                TimeUnit.MINUTES);

        await().untilAsserted(() -> {
            LOG.info("Assert updated logback.xml in pods");
            assertThat(client.pods()
                    .inNamespace(platformNamespace)
                    .list()
                    .getItems()
                    .stream()
                    .filter(pod -> pod.getMetadata().getName().startsWith(PLATFORM_RELEASE_NAME + "-"))) //
                    .allSatisfy(pod -> assertThatLogbackXmlContains(pod, "<root level=\"WARN\">"));
        });
    }

    private void assertThatLogbackXmlContains(final @NotNull Pod pod, final @NotNull String value) {
        try {
            Files.deleteIfExists(logbackFile);
            client.pods()
                    .inNamespace(platformNamespace)
                    .withName(pod.getMetadata().getName())
                    .file("/opt/hivemq/conf/logback.xml")
                    .copy(logbackFile);
            final var logbackXml = Files.readString(logbackFile);
            assertThat(logbackXml).contains(value);
        } catch (final IOException e) {
            throw new AssertionError("Could not copy logback.xml file from pod", e);
        }
    }
}
