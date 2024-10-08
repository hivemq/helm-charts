package com.hivemq.helmcharts.util;

import com.hivemq.helmcharts.testcontainer.DockerImageNames;
import com.hivemq.helmcharts.testcontainer.HelmChartContainer;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Deploys Nginx into a K8s cluster.
 */
public class NginxUtil {

    public static final @NotNull String NGINX_CONTAINER_NAME = "nginx";

    private NginxUtil() {
    }

    /**
     * Deploys Nginx and a matching service.
     * <p>
     * Each given file of {@code localPaths} is available under the URI {@code http://nginx-service/<filename>}.
     * <p>
     * The HTTPS configuration uses a self-signed certificate with {@code localhost} as CN.
     * HTTP clients probably have to skip certificate validation and hostname verification to connect successfully.
     *
     * @param client        the {@link KubernetesClient} used to deploy Nginx and its service
     * @param namespace     the namespace Nginx is deployed to
     * @param container     the container to serve the hosted files from (via hostPath volumes)
     * @param localPaths    the paths to the local files, that should be served via Nginx
     * @param withHttps     defines if the deployment should be configured with HTTPS
     * @param withBasicAuth defines if the deployment should be configured with Basic Auth
     */
    public static void deployNginx(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull HelmChartContainer container,
            final @NotNull List<Path> localPaths,
            final boolean withHttps,
            final boolean withBasicAuth) {
        assertThat(withHttps && withBasicAuth) //
                .as("It's not supported to configure HTTPS and basic auth at the same time") //
                .isFalse();

        // we have to copy the local paths from the host machine into the K3s container,
        // since this is the source used for the hostPath volumes
        for (final var localPath : localPaths) {
            container.copyFileToContainer(MountableFile.forHostPath(localPath), localPath.toAbsolutePath().toString());
        }

        if (withHttps) {
            final var nginxSecret = createNginxCertificateSecret(namespace);
            client.resource(nginxSecret).create();

            container.copyFileToContainer(MountableFile.forClasspathResource("nginx/nginx-https.conf"),
                    "/nginx-https-conf");
        } else if (withBasicAuth) {
            container.copyFileToContainer(MountableFile.forClasspathResource("nginx/nginx-htpasswd"),
                    "/nginx-htpasswd");
            container.copyFileToContainer(MountableFile.forClasspathResource("nginx/nginx-basic-auth.conf"),
                    "/nginx-basic-auth-conf");
        }

        final var nginxDeployment = createNginxDeployment(namespace, localPaths, withHttps, withBasicAuth);
        client.resource(nginxDeployment).create();

        final var nginxService = createNginxService(namespace);
        client.resource(nginxService).create();

        await().untilAsserted(() -> {
            final var nginx = client.apps().deployments().inNamespace(namespace).withName(NGINX_CONTAINER_NAME).get();
            assertThat(nginx).as("%s deployment", NGINX_CONTAINER_NAME).isNotNull();
            assertThat(nginx.getStatus()).as("%s status", NGINX_CONTAINER_NAME).isNotNull();
            assertThat(nginx.getStatus().getReadyReplicas()).isEqualTo(1);
        });
    }

    private static @NotNull Secret createNginxCertificateSecret(final @NotNull String namespace) {
        return new SecretBuilder().withType("kubernetes.io/tls")
                .withNewMetadata()
                .withNamespace(namespace)
                .withName("nginx-certificates")
                .endMetadata()
                .addToData("tls.crt", getResourceAsBase64("/nginx/nginx-tls.crt"))
                .addToData("tls.key", getResourceAsBase64("/nginx/nginx-tls.key"))
                .build();
    }

    private static String getResourceAsBase64(final @NotNull String name) {
        try {
            final var resource = NginxUtil.class.getResource(name);
            assertThat(resource).isNotNull();
            final var resourcePath = Path.of(resource.toURI());
            final var resourceContent = Files.readString(resourcePath);
            return Base64.getUrlEncoder().encodeToString(resourceContent.getBytes(UTF_8));
        } catch (final Exception e) {
            throw new AssertionError("Could not read resource", e);
        }
    }

    private static @NotNull Deployment createNginxDeployment(
            final @NotNull String namespace,
            final @NotNull List<Path> localPaths,
            final boolean withHttps,
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
        if (withHttps) {
            volumes.add(new VolumeBuilder().withName("nginx-certificates")
                    .withNewSecret()
                    .withSecretName("nginx-certificates")
                    .endSecret()
                    .build());
            volumeMounts.add(new VolumeMountBuilder().withName("nginx-certificates")
                    .withMountPath("/etc/nginx/certificates")
                    .build());
            volumes.add(new VolumeBuilder().withName("nginx-https-conf")
                    .withNewHostPath()
                    .withPath("/nginx-https-conf")
                    .withType("File")
                    .endHostPath()
                    .build());
            volumeMounts.add(new VolumeMountBuilder().withName("nginx-https-conf")
                    .withMountPath("/etc/nginx/conf.d/default.conf")
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
            volumes.add(new VolumeBuilder().withName("nginx-basic-auth-conf")
                    .withNewHostPath()
                    .withPath("/nginx-basic-auth-conf")
                    .withType("File")
                    .endHostPath()
                    .build());
            volumeMounts.add(new VolumeMountBuilder().withName("nginx-basic-auth-conf")
                    .withMountPath("/etc/nginx/conf.d/default.conf")
                    .build());
        }

        final var labels = Map.of("app", NGINX_CONTAINER_NAME);
        return new DeploymentBuilder() //
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(NGINX_CONTAINER_NAME)
                .endMetadata()
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                .withMatchLabels(labels)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withVolumes(volumes)
                .addNewContainer()
                .withName(NGINX_CONTAINER_NAME)
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
                .withSelector(Map.of("app", NGINX_CONTAINER_NAME))
                .withType("ClusterIP")
                .addNewPort()
                .withName("http")
                .withPort(80)
                .withProtocol("TCP")
                .endPort()
                .addNewPort()
                .withName("https")
                .withPort(443)
                .withProtocol("TCP")
                .endPort()
                .endSpec()
                .build();
    }
}
