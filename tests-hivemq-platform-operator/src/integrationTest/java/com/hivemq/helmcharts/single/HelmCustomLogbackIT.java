package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.PodUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("CustomConfig")
@Tag("CustomLogback")
class HelmCustomLogbackIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomLogbackConfig_platformUsingCustomLogbackXml() throws Exception {
        final var resource = getClass().getClassLoader().getResource("values/custom-logback.xml");
        assertThat(resource).isNotNull();
        final var customLogbackXml = Files.readString(Path.of(resource.getFile())).trim();
        // the init app initialized the custom logback.xml with scanning enabled
        final var expectedLogbackXml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                customLogbackXml.replace("<configuration>", "<configuration scan=\"true\" scanPeriod=\"20 seconds\">");

        installPlatformChartAndWaitToBeRunning("--set",
                "nodes.replicaCount=1",
                "--set-file",
                "config.customLogbackConfig=/files/custom-logback.xml");

        client.pods().inNamespace(platformNamespace).list().getItems().forEach(pod -> {
            final var podName = pod.getMetadata().getName();
            final var execResult = PodUtil.execute(client,
                    platformNamespace,
                    podName,
                    "hivemq",
                    "cat",
                    "/opt/hivemq/conf/logback.xml");
            try {
                assertThat(execResult.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(execResult.getOutput()).isNotNull();
                assertThat(execResult.getError()).isNull();
                assertThat(execResult.getOutput().trim()).isEqualTo(expectedLogbackXml);
            } finally {
                execResult.close();
            }
        });
    }
}
