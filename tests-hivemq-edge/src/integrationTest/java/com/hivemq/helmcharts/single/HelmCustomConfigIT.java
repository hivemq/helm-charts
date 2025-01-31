package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;

@Tag("CustomConfig")
@Tag("CustomValues")
class HelmCustomConfigIT extends AbstractHelmChartIT {

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomYml_hivemqRunning() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning();
        installPlatformChartAndWaitToBeRunning("--set-file",
                "config.overrideStatefulSet=/files/stateful-set-spec.yaml");

        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            final var foundContainer = statefulSet.getSpec()
                    .getTemplate()
                    .getSpec()
                    .getContainers()
                    .stream()
                    .filter(c -> c.getName().equalsIgnoreCase("hivemq"))
                    .findFirst();
            assertThat(foundContainer).isPresent();
            assertThat(foundContainer.get() //
                    .getPorts() //
                    .stream() //
                    .filter(p -> p.getName().startsWith("mqtt"))) //
                    .anyMatch(p -> p.getContainerPort().equals(1884));
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomXml_hivemqRunning() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning();
        installPlatformChartAndWaitToBeRunning("--set-file",
                "config.overrideHiveMQConfig=/files/hivemq-config-override.xml");

        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var configmap = client.configMaps()
                    .inNamespace(platformNamespace)
                    .withName("hivemq-configuration-" + PLATFORM_RELEASE_NAME)
                    .get();
            assertThat(configmap).isNotNull();
            final var xmlConfig = configmap.getData().get("config.xml");
            assertThat(xmlConfig).isNotNull().contains("<port>1884</port>");
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withExistingConfigMap_customResourceCreated() throws Exception {
        installPlatformOperatorChartAndWaitToBeRunning();
        final var configMap = K8sUtil.createConfigMap(client, platformNamespace, "hivemq-config-map.yml");
        final var configMapName = configMap.getMetadata().getName();

        installPlatformChartAndWaitToBeRunning("--set", "config.create=false", "--set", "config.name=" + configMapName);

        await().atMost(ONE_MINUTE).untilAsserted(() -> {
            final var hivemqCustomResource =
                    K8sUtil.getHiveMQPlatform(client, platformNamespace, PLATFORM_RELEASE_NAME).get();
            assertThat(hivemqCustomResource.getAdditionalProperties().get("spec")).isNotNull()
                    .asString()
                    .containsIgnoringCase("configMapName=" + configMapName);
            final var statefulSet =
                    client.apps().statefulSets().inNamespace(platformNamespace).withName(PLATFORM_RELEASE_NAME).get();
            assertThat(statefulSet).isNotNull();
            assertThat(K8sUtil.getHiveMQContainer(statefulSet.getSpec()).getVolumeMounts()) //
                    .anyMatch(volumeMount -> volumeMount.getName().equals("broker-configuration") &&
                            volumeMount.getMountPath().equals("/opt/hivemq/conf-k8s/"));

            assertThat(statefulSet.getSpec().getTemplate().getSpec().getVolumes()) //
                    .isNotNull() //
                    .anyMatch(volume -> volume.getName().equals("broker-configuration") &&
                            volume.getConfigMap().getName().equals(configMapName));
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void withCustomEnvVars_hivemqRunning() throws Exception {
        K8sUtil.createConfigMap(client, operatorNamespace, "operator-custom-env-var-config-map.yml");
        final var operatorStartedFuture = waitForOperatorLog(String.format(
                ".*Registered reconciler: 'hivemq-controller' for resource: 'class com.hivemq.platform.operator.v1.HiveMQPlatform' for namespace\\(s\\): \\[%s\\]",
                platformNamespace));
        installPlatformOperatorChartAndWaitToBeRunning("/files/custom-operator-env-vars-values.yaml");
        await().atMost(ONE_MINUTE).until(operatorStartedFuture::isDone);

        installPlatformChartAndWaitToBeRunning("/files/custom-platform-env-vars-values.yaml");

        // assert the custom operator configuration
        final var operatorDeployment =
                K8sUtil.getDeployment(client, operatorNamespace, getOperatorName());
        assertThat(operatorDeployment.getSpec().getTemplate().getSpec().getContainers().getFirst().getEnv()) //
                .anyMatch(envVar -> "QUARKUS_OPERATOR_SDK_NAMESPACES".equals(envVar.getName()) &&
                        "operator-config-map".equals(envVar.getValueFrom().getConfigMapKeyRef().getName()) &&
                        "namespace".equals(envVar.getValueFrom().getConfigMapKeyRef().getKey()));

        // assert the custom platform configuration
        final var statefulSet = K8sUtil.getStatefulSet(client, platformNamespace, PLATFORM_RELEASE_NAME);
        assertThat(K8sUtil.getHiveMQContainer(statefulSet.getSpec())
                .getEnv()).anyMatch(envVar -> "MY_CUSTOM_ENV_VAR".equals(envVar.getName()) &&
                "mycustomvalue".equals(envVar.getValue()));
    }
}
