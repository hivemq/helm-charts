# HiveMQ Platform Operator (new)

This Helm chart bootstraps the HiveMQ Platform Operator deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager. This chart also installs the HiveMQ Platform Custom Resource Definition (CRD).

The HiveMQ Platform Operator installs, updates and manages HiveMQ Platform clusters. The HiveMQ MQTT Platform makes it easy to move data to and from connected devices in a fast, efficient, and reliable manner.

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
helm install [RELEASE_NAME] hivemq/hivemq-platform-operator -n <namespace>
```

_See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-platform-operator/) for more details._

## Uninstall the Chart

```console
helm uninstall [RELEASE_NAME] -n <namespace>
```

This removes all Kubernetes components associated with the chart and deletes the release.

## Configuration

See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-platform-operator/) on configuration options. To view all configurable options with detailed comments, visit the chart's [values.yaml](https://github.com/hivemq/helm-charts/tree/main/charts/hivemq-platform-operator/values.yaml), or run this command:

```console
helm show values hivemq/hivemq-platform-operator
```
