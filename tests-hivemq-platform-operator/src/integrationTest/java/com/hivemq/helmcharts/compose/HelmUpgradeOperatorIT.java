package com.hivemq.helmcharts.compose;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("Upgrade")
class HelmUpgradeOperatorIT extends AbstractHelmChartIT {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(HelmUpgradeOperatorIT.class);

    @Override
    protected boolean cleanupPlatformChart() {
        return false;
    }

    @Override
    protected boolean cleanupOperatorChart() {
        return false;
    }

    @AfterEach
    void tearDown() throws Exception {
        helmChartContainer.uninstallRelease(operatorReleaseName, "--cascade", "foreground", "--namespace", namespace);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withDeployedOperator_upgradeUsingNewValues() throws Exception {
        helmChartContainer.installOperatorChart(operatorReleaseName, "--namespace", namespace);
        final var operatorName = "hivemq-" + operatorReleaseName;
        LOG.debug("Operator deployed successfully");

        final var deployment = client.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(operatorName)
                .waitUntilCondition(d -> d.getStatus() != null && d.getStatus().getAvailableReplicas() == 1,
                        3,
                        TimeUnit.MINUTES);
        assertThat(deployment.getStatus().getUpdatedReplicas()).isGreaterThan(0);
        assertThat(deployment.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .stream()).anyMatch(container -> container.getPorts()
                .stream()
                .anyMatch(containerPort -> containerPort.getContainerPort() == 8080));

        // upgrade chart and wait to be ready
        helmChartContainer.upgradeOperatorChart(operatorReleaseName,
                "--set",
                "http.port=8081",
                "--namespace",
                namespace);

        final Deployment upgradedDeployment = client.apps()
                .deployments()
                .inNamespace(namespace)
                .withName(operatorName)
                .waitUntilCondition(d -> d.getStatus().getUpdatedReplicas() == 1, 3, TimeUnit.MINUTES);

        assertThat(upgradedDeployment.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .stream()).anyMatch(container -> container.getPorts()
                .stream()
                .anyMatch(containerPort -> containerPort.getContainerPort() == 8081));
        assertThat(upgradedDeployment.getStatus().getAvailableReplicas()).isEqualTo(1);
    }
}
