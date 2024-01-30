# HiveMQ Helm charts

[![Artifact HUB](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/hivemq)](https://artifacthub.io/packages/search?repo=hivemq)

[![Correct Templates](https://github.com/hivemq/helm-charts/actions/workflows/verify.yml/badge.svg)](https://github.com/hivemq/helm-charts/actions/workflows/verify.yml) [![Smoke Test](https://github.com/hivemq/helm-charts/actions/workflows/smoke-test.yml/badge.svg)](https://github.com/hivemq/helm-charts/actions/workflows/smoke-test.yml) [![Integration Test](https://github.com/hivemq/helm-charts/actions/workflows/integration-test.yml/badge.svg?branch=master)](https://github.com/hivemq/helm-charts/actions/workflows/integration-test.yml)

This repository contains the HiveMQ Helm charts.

- The [HiveMQ Platform Operator Helm chart](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-platform-operator) (new)
  - This chart deploys the HiveMQ Platform Operator, that will install and manage your HiveMQ Platform clusters in Kubernetes.
  
- The [HiveMQ Platform Helm charts](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-platform) (new)
  - This chart requests the deployment of one HiveMQ Platform cluster (new) via the HiveMQ Platform Operator.  
      
- The [HiveMQ Operator Helm charts](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-operator) (legacy)
  - This chart deploys the legacy HiveMQ Operator and one HiveMQ Platform cluster.
  
- The [HiveMQ Swarm Helm charts](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-swarm)
  - This chart deploys a HiveMQ Swarm cluster, an advanced IoT testing and simulation tool.

## Documentation
See the [HiveMQ Kubernetes documentation](https://docs.hivemq.com/hivemq-platform-operator/introduction.html) for more detailed information.


## Install the HiveMQ Helm chart repository

Add the HiveMQ Helm Chart repository to your local Helm setup:

`helm repo add hivemq https://hivemq.github.io/helm-charts`

Refer to the individual Helm chart instructions for usage.

## Manifests

The manifest folder contains the rendered yaml files of the charts with default values. The manifests can be modified and configured and used for manual deployment without using helm. 

## Examples

The examples folder contains configuration examples for each of the charts. It also contains yaml files helpful for specific environments such as Openshift. 

## Tests
Integration tests are split into two different Gradle submodules, one for the HiveMQ Platform Operator (new) called [`tests-hivemq-platform-operator`](./tests-hivemq-platform-operator) and another one called [`tests-hivemq-operator`](./tests-hivemq-operator) for the HiveMQ Operator (legacy).

## Contributing

If you want to contribute to HiveMQ Helm Charts, see the [contribution guidelines](CONTRIBUTING.md).

**NOTE** Every PR to master must imply an increased minor version of the chart to indicate that the templating has changed.

## License

HiveMQ Helm Charts is licensed under the `APACHE LICENSE, VERSION 2.0`. A copy of the license can be found [here](LICENSE).
