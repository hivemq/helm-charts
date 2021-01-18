# HiveMQ Helm charts

[![Artifact HUB](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/hivemq)](https://artifacthub.io/packages/search?repo=hivemq)

This repository contains HiveMQ Helm charts.

## Usage

**Quick start**

Add the HiveMQ Helm Chart repository to your Helm installations:

`helm repo add hivemq https://hivemq.github.io/helm-charts`

Install the HiveMQ Helm Chart:

`helm upgrade --install hivemq hivemq/hivemq-operator`

The result of this command is a running HiveMQ cluster on your Kubernetes cluster that is maintained and managed by the HiveMQ Kubernetes Operator.

See the [HiveMQ documentation](https://www.hivemq.com/docs/operator/latest/kubernetes-operator/deploying.html#helm-chart) for more detailed instructions on how to use the helm chart.

## Contributing

If you want to contribute to HiveMQ Helm Charts, see the [contribution guidelines](CONTRIBUTING.md).

**NOTE** Every PR to master must imply an increased minor version of the chart to indicate that the templating has changed.

## License

HiveMQ Helm Charts is licensed under the `APACHE LICENSE, VERSION 2.0`. A copy of the license can be found [here](LICENSE).
