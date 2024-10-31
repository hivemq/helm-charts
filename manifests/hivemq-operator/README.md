# HiveMQ Operator Manifest files

This directory contains the following folders:

- `legacy`: contains the `v1beta1` version of CRD in case you need to deploy the HiveMQ operator on a Kubernetes cluster that does not support the v1 CustomResourceDefinition API.
- `operator`: contains a plain YAML deployment derived from the operator Helm chart, can be applied directly to a `hivemq` namespace to deploy the operator.
- `patch`: various strategic merge patch files demo-ing some functionality of the operator. Visit [Operator documentation](https://docs.hivemq.com/hivemq-operator/index.html) for more information.

## Prerequisites

- Helm 3.10.x

## Update manifest files

To create or update the manifest files with the latest changes from the Chart, run the command from the root of the project.

```shell
sh ./manifests/hivemq-operator/manifests.sh
```

## Install the HiveMQ Operator

1. Install the Custom Resource Definition (CRD) for the HiveMQ Cluster
    ```shell
    kubectl apply -f ./charts/hivemq-operator/crds/hivemq-cluster.yaml -n default
    ```
2. Install the HiveMQ Operator
    ```shell
    kubectl apply -f ./manifests/hivemq-operator/*.yaml -n default
    ```

**NOTE**: These manifest files are using the `default` namespace out of the box, given that the ServiceAccount requires a specific namespace. Change these manifests accordingly if you are planning to install them in a different namespace.
