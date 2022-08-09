# Examples
## Modifying the default HiveMQ K8s Image Non-Root Image
Security is important [[1](https://snyk.io/blog/10-kubernetes-security-context-settings-you-should-understand)] when running containers in Kubernetes. 
For this reason we provide the documentation on how to modify the HiveMQ image without increasing its size.
### Build the image

- Get the latest version from the docker registry https://hub.docker.com/r/hivemq/hivemq4/tags?page=1&name=k8s, for instance `hivemq/hivemq4:k8s-<version>`.
- Build the custom image using the command:
```bash
docker build \
  --build-arg HIVEMQ_IMAGE=hivemq/hivemq4:k8s-<version> \
  --build-arg JAVA_IMAGE=openjdk:11-jre-slim \
  -t <custom-org>:<custom-tag> -f example_nonroot_k8s.dockerfile .
```
### Configure the Helm-Chart
- Configure the image name on the helm-chart `values.yml`
  ```
  hivemq:
    image: <custom-org>:<custom-tag>
  ```
- Set up the [pod and container security context](https://kubernetes.io/docs/tasks/configure-pod-container/security-context/) on the helm-chart values
  ```yaml
  podSecurityContext:
    fsGroup: 10000
    runAsNonRoot: true
    runAsGroup: 10000
    runAsUser: 10001
  containerSecurityContext:
     runAsNonRoot: true
     runAsGroup: 10000
     runAsUser: 10001
     allowPrivilegeEscalation: false
     privileged: false
  ```
  Note: The HiveMQ user and group is `10000`, the user can be different, but should be on the group `10000`

## References
[1] [10 Kubernetes Security Context settings you should understand](https://snyk.io/blog/10-kubernetes-security-context-settings-you-should-understand)