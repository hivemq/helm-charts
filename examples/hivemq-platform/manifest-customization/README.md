# Example for customization of manifest files

This example shows how to use customized manifest files to deploy a HiveMQ Platform.

## Scenario

We are using a custom `values.yaml` file that defines two HiveMQ TPC listeners on ports 1883 and 1884.
For the second listener on port 1884 we want to activate the PROXY protocol.

## Usage

* Call `custom-manifests.sh` to create the manifest files from your `values.yaml` file.
* Call `add-proxy-protocol.sh` to add the PROXY protocol to the crated manifest files.

## Installation

* Install a HiveMQ Platform Operator in your Kubernetes cluster using the `hivemq-platform-operator` Helm chart.
* Call `install.sh` to deploy the HiveMQ Platform.

## Uninstallation

* Call `uninstall.sh` to delete the HiveMQ Platform.
* Optionally uninstall the HiveMQ Platform Operator from your Kubernetes cluster using the `hivemq-platform-operator` Helm chart.
