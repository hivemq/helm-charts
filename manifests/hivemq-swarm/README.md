# HiveMQ Swarm Manifests

This folder contains the manifest files that can be customized to install HiveMQ Swarm without using Helm.

## Prerequisites

- Helm 3.10.x

## Update manifest files

To create or update the manifest files with the latest changes from the Chart, run the command from the root of the project.

```shell
sh ./manifests/hivemq-swarm/manifests.sh
```

## Install the HiveMQ Swarm

1. Install the HiveMQ Swarm
    ```shell
    kubectl apply -f ./manifests/hivemq-swarm/*.yaml -n default
    ```

**NOTE**: These manifest files are using the `default` namespace out of the box, given that the ServiceAccount requires a specific namespace. Change these manifests accordingly if you are planning to install them in a different namespace.
