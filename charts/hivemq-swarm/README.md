# HiveMQ Swarm

[HiveMQ Swarm](https://www.hivemq.com/docs/swarm/) is an advanced IoT testing and simulation tool that gives you the load and reliability testing ability you need to determine the resilience and capacity of your complete IoT system.

## TL;DR

```console
$ helm repo add hivemq https://hivemq.github.io/helm-charts
$ helm upgrade --install swarm hivemq/hivemq-swarm
```

See [here](https://www.hivemq.com/docs/swarm/clustering#deploy-on-k8s) for a more detailed getting started and configuration guidance on this Helm Chart.

## Introduction

This chart bootstraps a [HiveMQ Swarm](https://github.com/hivemq/hivemq-swarm) deployment on a [Kubernetes](http://kubernetes.io) cluster using the [Helm](https://helm.sh) package manager.

Therefore, this chart provision:
- A HiveMQ Swarm commander with an enabled [REST-API](https://www.hivemq.com/docs/swarm/rest-service) to start scenarios 
- A set of HiveMQ Swarm agents
- (Optional) Prometheus-operator integration for monitoring
  - A Prometheus Operator sub-chart if you want to deploy a dedicated Prometheus instance
  - A pre-defined Grafana dashboard, called `HiveMQ Swarm`, deployed along with the sub-chart

## Prerequisites

- Running Kubernetes cluster version 1.13.0 or higher on the cloud provider of your choice
- Helm version 3 or higher

## Installing the Chart

To install the chart with the release name `swarm`:

```console
$ helm repo add hivemq https://hivemq.github.io/helm-charts
$ helm upgrade --install swarm hivemq/hivemq-swarm
```

These commands deploy HiveMQ Swarm on the Kubernetes cluster in the default configuration.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `swarm` deployment:

```console
$ helm delete swarm
```
