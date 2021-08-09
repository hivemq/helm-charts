# HiveMQ operator

See [here](https://www.hivemq.com/docs/operator/) for detailed getting started and configuration guidance.

This chart can provision

- HiveMQCluster CustomResourceDefinition
- HiveMQ operator as Deployment
- a configurable HiveMQCluster custom resource representing a cluster
- (optionally) Prometheus-operator integration
  - prometheus-operator sub-chart if you want to deploy a dedicated Prometheus instance
  - Grafana dashboard deployed along with the sub-chart
