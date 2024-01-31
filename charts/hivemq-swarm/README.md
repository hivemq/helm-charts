# HiveMQ Swarm

This Helm chart bootstraps a [HiveMQ Swarm](https://docs.hivemq.com/hivemq-swarm/) deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager. 

[HiveMQ Swarm](https://docs.hivemq.com/hivemq-swarm/) is an advanced IoT testing and simulation tool that provides load and reliability testing for your IoT architectures.

This chart deploys:
- a HiveMQ Swarm commander with the [REST-API](https://docs.hivemq.com/hivemq-swarm/latest/rest-service.html) enabled to start scenarios
- a number of HiveMQ Swarm agents
- an optional Prometheus for monitoring
    - with an optional Prometheus Operator as a dedicated Prometheus instance
    - with a pre-defined Grafana dashboard, called `HiveMQ Swarm`


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

## Configuration

See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-swarm/latest/clustering.html#deploy-on-k8s) on configuration options. To view all configurable options with detailed comments, visit the chart's [values.yaml](https://github.com/hivemq/helm-charts/blog/master/charts/hivemq-swarm/values.yaml), or run this command:

```console
helm show values hivemq/hivemq-swarm
```



