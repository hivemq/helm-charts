package com.hivemq.helmcharts.util;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Deploys Nginx into a K8s cluster.
 */
public class NginxUtil {

    private NginxUtil() {
    }

    /**
     * Deploys Nginx and a matching service.
     * <p/>
     * Each given file of {@code localPaths} is available under the URI {@code http://nginx-service/<filename>}.
     *
     * @param client        the {@link KubernetesClient} used to deploy Nginx and its service
     * @param namespace     the namespace Nginx is deployed to
     * @param container     the container to serve the hosted files from (via hostPath volumes)
     * @param localPaths    the paths to the local files, that should be served via Nginx
     * @param withBasicAuth defines if the deployment should be configured with Basic Auth
     */
    public static void deployNginx(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull HelmChartContainer container,
            final @NotNull List<Path> localPaths,
            final boolean withBasicAuth) {
        // we have to copy the local paths from the host machine into the K3s container,
        // since this is the source used for the hostPath volumes
        for (final var localPath : localPaths) {
            container.copyFileToContainer(MountableFile.forHostPath(localPath), localPath.toAbsolutePath().toString());
        }

        if (withBasicAuth) {
            container.copyFileToContainer(MountableFile.forClasspathResource("nginx/nginx-htpasswd"),
                    "/nginx-htpasswd");
            container.copyFileToContainer(MountableFile.forClasspathResource("nginx/nginx-default.conf"),
                    "/nginx-default-conf");
        }

        final var nginxDeployment = createNginxDeployment(namespace, localPaths, withBasicAuth);
        client.resource(nginxDeployment).create();

        final var nginxService = createNginxService(namespace);
        client.resource(nginxService).create();

        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            final Deployment nginx = client.apps().deployments().inNamespace(namespace).withName("nginx").get();
            assertThat(nginx).isNotNull();
            assertThat(nginx.getStatus().getReadyReplicas()).isEqualTo(1);
        });
    }

    private static @NotNull Deployment createNginxDeployment(
            final @NotNull String namespace,
            final @NotNull List<Path> localPaths,
            final boolean withBasicAuth) {
        final var volumes = new ArrayList<Volume>();
        final var volumeMounts = new ArrayList<VolumeMount>();

        for (final var localPath : localPaths) {
            final var fileName = localPath.getFileName().toString();
            final var name = "volume-" + FilenameUtils.removeExtension(fileName).replace(".", "");
            // see https://kubernetes.io/docs/concepts/storage/volumes/#hostpath
            volumes.add(new VolumeBuilder().withName(name)
                    .withNewHostPath()
                    .withPath(localPath.toAbsolutePath().toString())
                    .withType("File")
                    .endHostPath()
                    .build());
            volumeMounts.add(new VolumeMountBuilder().withName(name)
                    .withMountPath("/usr/share/nginx/html/" + fileName)
                    .build());
        }
        if (withBasicAuth) {
            volumes.add(new VolumeBuilder().withName("nginx-htpasswd")
                    .withNewHostPath()
                    .withPath("/nginx-htpasswd")
                    .withType("File")
                    .endHostPath()
                    .build());
            volumeMounts.add(new VolumeMountBuilder().withName("nginx-htpasswd")
                    .withMountPath("/etc/nginx/.htpasswd")
                    .build());
            volumes.add(new VolumeBuilder().withName("nginx-default-conf")
                    .withNewHostPath()
                    .withPath("/nginx-default-conf")
                    .withType("File")
                    .endHostPath()
                    .build());
            volumeMounts.add(new VolumeMountBuilder().withName("nginx-default-conf")
                    .withMountPath("/etc/nginx/conf.d/default.conf")
                    .build());
        }

        return new DeploymentBuilder() //
                .withNewMetadata()
                .withNamespace(namespace)
                .withName("nginx")
                .endMetadata()
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                .withMatchLabels(Collections.singletonMap("app", "nginx"))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(Collections.singletonMap("app", "nginx"))
                .endMetadata()
                .withNewSpec()
                .withVolumes(volumes)
                .addNewContainer()
                .withName("nginx")
                .withImage(DockerImageNames.NGINX_DOCKER_IMAGE.asCanonicalNameString())
                .addNewPort()
                .withName("http")
                .withContainerPort(80)
                .endPort()
                .withVolumeMounts(volumeMounts)
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private static @NotNull Service createNginxService(final @NotNull String namespace) {
        return new ServiceBuilder() //
                .withNewMetadata()
                .withNamespace(namespace)
                .withName("nginx-service")
                .endMetadata()
                .withNewSpec()
                .withSelector(Collections.singletonMap("app", "nginx"))
                .withType("ClusterIP")
                .addNewPort()
                .withName("http")
                .withPort(80)
                .withProtocol("TCP")
                .endPort()
                .endSpec()
                .build();
    }
}
