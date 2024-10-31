# HiveMQ Operator Platform and HiveMQ Platform Manifests

This folder contains the manifest files that can be customized to install one HiveMQ Platform without using Helm.

## Prerequisites

- Helm 3.10.x

## Update manifest files

To create or update the manifest files with the latest changes from the Chart, run the command from the root of the project.

```shell
sh ./manifests/hivemq-platform-operator/manifests.sh
sh ./manifests/hivemq-platform/manifests.sh
```

## Install the HiveMQ Platform Operator and HiveMQ Platform

1. Install the Custom Resource Definition (CRD) for the HiveMQ Platform
    ```shell
    kubectl apply -f ./charts/hivemq-platform-operator/crds/hivemq-platforms.hivemq.com-v*.yml -n default
    ```
2. Install the HiveMQ Platform Operator
    ```shell
    kubectl apply -f ./manifests/hivemq-platform-operator/*.yml -n default
    ```
3. Install the HiveMQ Platform
    ```shell
    kubectl apply -f ./manifests/hivemq-platform/*.yml
    ```
**NOTE**: These manifest files are using the `default` namespace out of the box, given that the ServiceAccount requires a specific namespace. Change these manifests accordingly if you are planning to install them in a different namespace.
