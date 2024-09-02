package com.hivemq.helmcharts.single;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import com.hivemq.helmcharts.util.K8sUtil;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("ServiceAccount")
class HelmCustomServiceAccountIT extends AbstractHelmChartIT {

    private static final @NotNull String SERVICE_ACCOUNT_NAME = "my-custom-sa";

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withNonExistingCustomServiceAccountThenCreate_hivemqRunning() throws Exception {
        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME, "--namespace", operatorNamespace);
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        final var hivemqCustomResource =
                K8sUtil.waitForHiveMQPlatformState(client, platformNamespace, PLATFORM_RELEASE_NAME, "ERROR");
        //noinspection unchecked
        assertThat((Map<String, String>) hivemqCustomResource.getAdditionalProperties().get("status")).containsValues(
                String.format("The custom resource spec is invalid: The ServiceAccount '%s' does not exist",
                        SERVICE_ACCOUNT_NAME),
                "INVALID_CUSTOM_RESOURCE_SPEC");

        // create missing ServiceAccount
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withNonExistingPermissionsThenCreate_hivemqRunning() throws Exception {
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);

        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME,
                "--set",
                "hivemqPlatformServiceAccount.create=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.create=false",
                "--namespace",
                operatorNamespace);
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        final var hivemqCustomResource =
                K8sUtil.waitForHiveMQPlatformState(client, platformNamespace, PLATFORM_RELEASE_NAME, "ERROR");
        //noinspection unchecked
        assertThat((Map<String, String>) hivemqCustomResource.getAdditionalProperties().get("status")).containsValues(
                String.format(
                        "The custom resource spec is invalid: The ServiceAccount '%s' must have the Pod resource permissions: [get, patch, update]",
                        SERVICE_ACCOUNT_NAME),
                "INVALID_CUSTOM_RESOURCE_SPEC");

        // create missing permissions
        createRole();
        createRoleBinding();

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withCreationAndValidationDisabled_withCorrectExternalPermissions_hivemqRunning()
            throws Exception {
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);
        createRole();
        createRoleBinding();

        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME,
                "--set",
                "hivemqPlatformServiceAccount.create=false",
                "--set",
                "hivemqPlatformServiceAccount.validate=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.create=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.validate=false",
                "--namespace",
                operatorNamespace);
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void platformCharts_withCreateAndValidateDisabled_hivemqRunning() throws Exception {
        helmChartContainer.installPlatformOperatorChart(OPERATOR_RELEASE_NAME,
                "--set",
                "hivemqPlatformServiceAccount.create=false",
                "--set",
                "hivemqPlatformServiceAccount.validate=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.create=false",
                "--set",
                "hivemqPlatformServiceAccount.permissions.validate=false",
                "--namespace",
                operatorNamespace);
        helmChartContainer.installPlatformChart(PLATFORM_RELEASE_NAME,
                "--set",
                "nodes.serviceAccountName=" + SERVICE_ACCOUNT_NAME,
                "--set",
                "nodes.replicaCount=1",
                "--namespace",
                platformNamespace);

        // create the ServiceAccount and permissions correctly
        K8sUtil.createServiceAccount(client, platformNamespace, SERVICE_ACCOUNT_NAME);
        createRole();
        createRoleBinding();

        K8sUtil.waitForHiveMQPlatformStateRunning(client, platformNamespace, PLATFORM_RELEASE_NAME);

        // assert that the ServiceAccount and permissions are working
        assertPlatformPodAnnotations();
    }

    private void createRole() {
        final var role = client.resource(new RoleBuilder().withNewMetadata()
                .withName("pod-resource-role")
                .withNamespace(platformNamespace)
                .endMetadata()
                .withRules(new PolicyRuleBuilder().withApiGroups("")
                        .withResources("pods")
                        .withVerbs("get", "patch", "update")
                        .build())
                .build()).create();
        assertThat(role).isNotNull();
    }

    private void createRoleBinding() {
        final var roleBinding = client.resource(new RoleBindingBuilder().withNewMetadata()
                .withName("pod-resource-role-binding")
                .withNamespace(platformNamespace)
                .endMetadata()
                .withSubjects(new SubjectBuilder() //
                        .withKind("ServiceAccount") //
                        .withName(SERVICE_ACCOUNT_NAME) //
                        .build())
                .withRoleRef(new RoleRefBuilder().withName("pod-resource-role").withKind("Role").build())
                .build()).create();
        assertThat(roleBinding).isNotNull();
    }

    private void assertPlatformPodAnnotations() {
        // asserts an annotation that is set by the PodReconciler in the Platform Pods
        // when the ServiceAccount and permissions are set up correctly
        await().untilAsserted(() -> client.pods()
                .inNamespace(platformNamespace)
                .list()
                .getItems()
                .forEach(pod -> assertThat(pod.getMetadata().getAnnotations()) //
                        .containsKey("hivemq/platform-operator-init-app-version")));
    }
}
