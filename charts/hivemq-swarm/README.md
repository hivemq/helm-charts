# HiveMQ Swarm

This Helm chart bootstraps a [HiveMQ Swarm](https://docs.hivemq.com/hivemq-swarm/) deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager. 

[HiveMQ Swarm](https://docs.hivemq.com/hivemq-swarm/) is an advanced IoT testing and simulation tool that provides load and reliability testing for your IoT architectures.

This chart deploys:
- a HiveMQ Swarm commander with the [REST-API](https://docs.hivemq.com/hivemq-swarm/latest/rest-service.html) enabled to start scenarios
- a number of HiveMQ Swarm agents

See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-swarm/) for
more details.

## Prerequisites

- Kubernetes 1.23+
- Helm 3

## Repository Info

```console
helm repo add hivemq https://hivemq.github.io/helm-charts
helm repo update
```

_See the Helm [`documentation`](https://helm.sh/docs/helm/helm_repo/) for more details._

## Install the Chart

```console
helm install [RELEASE_NAME] hivemq/hivemq-swarm -n <namespace>
```

_See the [documentation](https://docs.hivemq.com/hivemq-swarm/latest/clustering.html#deploy-on-k8s) for more detailed information on getting started with this Helm Chart._

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
curl -L -o hivemq-swarm-x.y.z.prov https://github.com/hivemq/helm-charts/releases/download/hivemq-swarm-x.y.z/hivemq-swarm-x.y.z.tgz.prov
curl -L -o hivemq-swarm-x.y.z.tgz https://github.com/hivemq/helm-charts/releases/download/hivemq-swarm-x.y.z/hivemq-swarm-x.y.z.tgz
helm verify hivemq-swarm-x.y.z.tgz
```

**NOTE** Helm does not support GPG version 2 or higher so you have to convert your GPG keyring to the legacy GPG format:
```shell
gpg --export >~/.gnupg/pubring.gpg
```
_See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-swarm/latest/clustering.html#deploy-on-k8s) for more details._

## Configuration

See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-swarm/latest/clustering.html#deploy-on-k8s) on configuration options. To view all configurable options with detailed comments, visit the chart's [values.yaml](https://github.com/hivemq/helm-charts/blog/master/charts/hivemq-swarm/values.yaml), or run this command:

```console
helm show values hivemq/hivemq-swarm
```



