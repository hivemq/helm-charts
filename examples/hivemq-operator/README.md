# Examples

## Creating a custom HiveMQ k8s image with non-root user and group
Security is important [[1](https://snyk.io/blog/10-kubernetes-security-context-settings-you-should-understand)] when
running containers in Kubernetes. 
For this reason we provide the documentation on how to create a HiveMQ image with non-root user and group without
increasing the size of the image.

**NOTE** The example shown here below is based on the [HiveMQ Operator chart](../../charts/hivemq-operator) (legacy).

### Build your custom image

- Check which is the latest version of the HiveMQ k8s image provided on the Docker registry https://hub.docker.com/r/hivemq/hivemq4/tags?page=1&name=k8s `hivemq/hivemq4:k8s-<version>`.
- Build the custom image using the command:
  ```bash
  docker build \
    --build-arg HIVEMQ_IMAGE=hivemq/hivemq4:k8s-<version> \
    --build-arg JAVA_IMAGE=openjdk:11-jre-slim \
    -t <custom-org>:<custom-tag> -f example_nonroot_k8s.dockerfile .
  ```
- Push your created custom Docker image to your Docker registry, for more info check the [official documentation [2]](https://docs.docker.com/engine/reference/commandline/push/):
  ```bash
  docker image push <registry-host>:5000/<custom-org>:<custom-tag>
  ```
  
### Configure the Helm-Chart
Override the default Docker image name of the HiveMQ operator helm chart, by creating a custom 'values.yml' file and configure the podSecurityContext according to your image.
Use the custom values file as described on the [HiveMQ Operator documentation [3]](https://docs.hivemq.com/hivemq-operator/deploying.html#deploy-operator)

- Configure the image name on the helm-chart `values.yml` for the HiveMQ operator helm-chart
  ```
  hivemq:
    image: <custom-org>:<custom-tag>
  ```
- Set up the [pod and container security context [4]](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/) on the helm-chart values
  ```yaml
  podSecurityContext:
    fsGroup: 10000
    runAsNonRoot: true
    runAsGroup: 10000
    runAsUser: 10000
  containerSecurityContext:
     runAsNonRoot: true
     runAsGroup: 10000
     runAsUser: 10000
     allowPrivilegeEscalation: false
     privileged: false
  ```
  Note: The HiveMQ user and group is `10000:10000`. The user can be different, but should be part of the group `10000`

- Install or upgrade your HiveMQ helm-chart:
  ```bash
  helm upgrade --install -f myCustomValues.yaml hivemq hivemq/hivemq-operator
  ```

## References
- [1] [10 Kubernetes Security Context settings you should understand](https://snyk.io/blog/10-kubernetes-security-context-settings-you-should-understand)
- [2] [Docker command line official documentation](https://docs.docker.com/engine/reference/commandline)
- [3] [HiveMQ Operator documentation](https://docs.hivemq.com/hivemq-operator/deploying.html#deploy-operator)
- [4] [Kubernetes pod and container security context](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/)
