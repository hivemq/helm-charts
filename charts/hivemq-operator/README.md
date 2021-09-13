# HiveMQ operator

See [here](https://www.hivemq.com/docs/operator/) for detailed getting started and configuration guidance.

This chart can provision

- HiveMQCluster CustomResourceDefinition
- HiveMQ operator as Deployment
- a configurable HiveMQCluster custom resource representing a cluster
- (optionally) Prometheus-operator integration
  - prometheus-operator sub-chart if you want to deploy a dedicated Prometheus instance
  - Grafana dashboard deployed along with the sub-chart

## Installing on Kubernetes clusters <1.16

Since chart version 0.9.0, we are shipping a v1 CustomResourceDefinition by default in order to be compatible with future Kubernetes versions.

If you are deploying the HiveMQ operator on a Kubernetes cluster that does not support the v1 CustomResourceDefinition API, you can use the following steps to deploy the chart:

The v1beta1 CRD still supports all the same fields as the v1 CRD.

```sh
# 1. Deploy the v1beta1 CRD manually
kubectl apply -f https://raw.githubusercontent.com/hivemq/helm-charts/master/manifests/legacy/v1beta1-hivemq-cluster.yaml
# 2. Deploy the Helm chart, skip CRD deployment
helm install hivemq-operator hivemq-operator/hivemq-operator --skip-crds
```+