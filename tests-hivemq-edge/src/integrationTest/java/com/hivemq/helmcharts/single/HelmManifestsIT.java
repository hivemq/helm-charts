package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class HelmManifestsIT extends AbstractHelmChartIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withLocalCharts_hivemqRunning() throws Exception {
        final var execResult = helmChartContainer.execInContainer("/bin/kubectl",
                "apply",
                "-f",
                "/charts/hivemq-platform-operator/crds");
        assertThat(execResult.getExitCode()).isEqualTo(0);
        final var operatorOut = helmChartContainer.execInContainer("/bin/kubectl",
                "apply",
                "-f",
                "/" + HelmChartContainer.MANIFEST_FILES + "/hivemq-platform-operator");
        assertThat(operatorOut.getExitCode()).isEqualTo(0);
        final var platformOut = helmChartContainer.execInContainer("/bin/kubectl",
                "apply",
                "-f",
                "/" + HelmChartContainer.MANIFEST_FILES + "/hivemq-platform");
        assertThat(platformOut.getExitCode()).isEqualTo(0);

        final var client = helmChartContainer.getKubernetesClient();
        K8sUtil.waitForHiveMQPlatformStateRunning(client, "default", "hivemq-my-platform");
    }
}
