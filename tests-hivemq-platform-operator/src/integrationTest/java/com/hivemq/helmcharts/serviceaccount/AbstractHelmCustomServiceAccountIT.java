package com.hivemq.helmcharts.serviceaccount;

import com.hivemq.helmcharts.AbstractHelmChartIT;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import org.jetbrains.annotations.NotNull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AbstractHelmCustomServiceAccountIT extends AbstractHelmChartIT {

    protected static final @NotNull String SERVICE_ACCOUNT_NAME = "my-custom-sa";

    @Override
    protected boolean installPlatformOperatorChart() {
        return false;
    }

    protected void createRole() {
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

    protected void createRoleBinding() {
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

    protected void assertPlatformPodAnnotations() {
        // asserts an annotation that is set by the PodReconciler in the Platform Pods
        // when the ServiceAccount and permissions are set up correctly
        await().untilAsserted(() -> client.pods()
                .inNamespace(platformNamespace)
                .list()
                .getItems()
                .forEach(pod -> assertThat(pod.getMetadata().getAnnotations()) //
                        .containsKey("operator.platform.hivemq.com/init-app-version")));
    }
}
