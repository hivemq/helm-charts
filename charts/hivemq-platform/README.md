# HiveMQ Platform (new)

This Helm chart bootstraps the HiveMQ Platform deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager. With the default settings, the HiveMQ Platform Operator then installs a 2 node HiveMQ Platform cluster that is suitable for testing. 

This chart can be used multiple times to deploy several HiveMQ Platform clusters. One HiveMQ Platform Operator can manage many HiveMQ deployments.

See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-platform-operator/) for
more details.

## Prerequisites

- Kubernetes 1.23+
- Helm 3.10.x

## Repository Info

```console
helm repo add hivemq https://hivemq.github.io/helm-charts
helm repo update
```

_See the Helm [`documentation`](https://helm.sh/docs/helm/helm_repo/) for more details._

## Install the Chart

```console
helm install [RELEASE_NAME] hivemq/hivemq-platform -n <namespace>
```

_See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-platform-operator/) for more details._

## Uninstall the Chart

```console
helm uninstall [RELEASE_NAME] -n <namespace>
```

This removes all Kubernetes components associated with the chart and deletes the release.

## Verify the chart

```console
curl -L -o public.pgp https://www.hivemq.com/public.pgp
gpg --import public.pgp
gpg --export >~/.gnupg/pubring.gpg
curl -L -o hivemq-platform-x.y.z.prov https://github.com/hivemq/helm-charts/releases/download/hivemq-platform-x.y.z/hivemq-platform-x.y.z.tgz.prov
curl -L -o hivemq-platform-x.y.z.tgz https://github.com/hivemq/helm-charts/releases/download/hivemq-platform-x.y.z/hivemq-platform-x.y.z.tgz
helm verify hivemq-platform-x.y.z.tgz
```

**NOTE** Helm does not support GPG version 2 or higher so you have to convert your GPG keyring to the legacy GPG format:
```shell
gpg --export >~/.gnupg/pubring.gpg
```
_See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-platform-operator/) for more details._

## Configuration

See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-platform-operator/) on configuration options. To view all configurable options with detailed comments, visit the chart's [values.yaml](https://github.com/hivemq/helm-charts/blog/master/charts/hivemq-platform/values.yaml), or run this command:

```console
helm show values hivemq/hivemq-platform
```
