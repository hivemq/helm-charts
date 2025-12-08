# HiveMQ Helm charts

[![Artifact HUB](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/hivemq)](https://artifacthub.io/packages/search?repo=hivemq)

[![HiveMQ Helm Charts](https://github.com/hivemq/helm-charts/actions/workflows/dispatcher.yml/badge.svg)](https://github.com/hivemq/helm-charts/actions/workflows/dispatcher.yml)

This repository contains the HiveMQ Helm charts.

- The [HiveMQ Platform Operator Helm chart](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-platform-operator)
  - This chart deploys the latest HiveMQ Platform Operator that installs and manages your HiveMQ Platform clusters in Kubernetes.

- The [HiveMQ Platform Helm charts](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-platform)
  - This chart specifies the HiveMQ Platform deployment that the HiveMQ Platform Operator then installs.

- The [HiveMQ Operator Helm charts](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-operator) (legacy)
  - This chart deploys the legacy HiveMQ Operator and a HiveMQ cluster.
    The legacy HiveMQ Operator and its associated Helm chart were officially retired in April 2025 and are no longer maintained or updated.
    To migrate to the current HiveMQ Platform Operator for Kubernetes, see [HiveMQ Legacy Operator to Platform Operator Migration Guide](https://docs.hivemq.com/hivemq-operator/migration-guide.html).

- The [HiveMQ Swarm Helm charts](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-swarm)
  - This chart deploys a HiveMQ Swarm cluster, an advanced IoT testing and simulation tool.

- The [HiveMQ Edge Helm charts](https://github.com/hivemq/helm-charts/blob/master/charts/hivemq-edge)
  - This chart deploys a HiveMQ Edge instance, an advanced IoT edge computing solution.

## Documentation

See the [HiveMQ Kubernetes documentation](https://docs.hivemq.com/hivemq-platform-operator/introduction.html) for more detailed information.

## Register the HiveMQ Helm chart repository

Add the HiveMQ Helm Chart repository to your local Helm setup:

`helm repo add hivemq https://hivemq.github.io/helm-charts`  
`helm repo update`

Refer to the individual Helm chart instructions for usage.

## Manifests

The manifest folder contains the rendered yaml files of the Helm charts with default values.
The manifests can be modified, configured, and used for manual deployment with kubectl.

## Examples

The examples folder contains configuration examples for each of the Helm charts.
This folder also contains yaml files that are helpful for specific environments such as Openshift.

## Tests

The integration tests for the HiveMQ Platform Operator are in the submodule [
`tests-hivemq-platform-operator`](./tests-hivemq-platform-operator).

## Verifying charts

We sign the HiveMQ Helm charts with PGP keys during the packaging.
These signed charts have a `.prov` file generated alongside the packaged chart, which includes the chart and the signature.

To verify the authenticity of the charts, download the [public PGP key](https://www.hivemq.com/public.pgp) and import the public key into your GPG keyring.
Then use `helm verify` or `helm install --verify` command to verify the chart integrity.
Check our documentation for more detailed information.

**NOTE** Helm does not support GPG version 2 or higher so you have to convert your GPG keyring to the legacy GPG format:

```shell
gpg --export >~/.gnupg/pubring.gpg
```

## Contributing

If you want to contribute to HiveMQ Helm Charts, see the [contribution guidelines](CONTRIBUTING.md).

**NOTE** Every PR to master must imply an increased minor version of the chart to indicate that the templating has changed.

## License

HiveMQ Helm Charts is licensed under the `APACHE LICENSE, VERSION 2.0`. A copy of the license can be found [here](LICENSE).
