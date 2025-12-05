package com.hivemq.helmcharts.operator;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

class HelmApplyCrdUpdateIT extends AbstractHelmApplyCrdIT {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withOutdatedCrd_operatorIsRunning() throws Exception {
        final var rootPath = Path.of("..").toAbsolutePath();
        final var crdPath =
                rootPath.resolve("charts/hivemq-platform-operator/crds/%s-%s.yml".formatted(CRD_NAME, CRD_VERSION));
        final var crdYaml = Files.readString(crdPath);
        final var crd = client.getKubernetesSerialization().unmarshal(crdYaml, CustomResourceDefinition.class);
        crd.getSpec()
                .getVersions()
                .forEach(version -> version.getSchema()
                        .getOpenAPIV3Schema()
                        .getProperties()
                        .get("status")
                        .getProperties()
                        .get("crdVersion")
                        .setEnum(List.of(jsonNodeFactory.textNode("V1_0_0"), jsonNodeFactory.textNode("V1_0_1"))));

        final var customCrdResource = client.apiextensions().v1().customResourceDefinitions().resource(crd);
        customCrdResource.create();
        customCrdResource.waitUntilCondition(c -> c != null &&
                c.getStatus() != null &&
                c.getStatus().getConditions() != null &&
                c.getStatus()
                        .getConditions()
                        .stream()
                        .filter(condition -> "Established".equals(condition.getType()))
                        .anyMatch(condition -> "True".equals(condition.getStatus())), 10, TimeUnit.SECONDS);

        installAndAssertRunningOperator(
                ".*HiveMQ Platform CRD is not on version .* \\(deployed versions: V1_0_0, V1_0_1\\)");
    }
}
