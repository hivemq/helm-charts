# HiveMQ Edge Manifests

This folder contains the manifest files that can be customized to install HiveMQ Edge without using Helm.

## Prerequisites

- Helm 3.10.x

## Update manifest files

To create or update the manifest files with the latest changes from the Chart, run the command from the root of the project.

```shell
sh ./manifests/hivemq-edge/manifests.sh
```

## Install HiveMQ Edge

Install HiveMQ Edge 

```shell
kubectl apply -f ./manifests/hivemq-edge/*.yml -n default
```
