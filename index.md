# HiveMQ Helm Repository

## Add the HiveMQ Helm repository

```
helm repo add hivemq https://hivemq.github.io/helm-charts
```

### In case the repository already exists, upgrade to the latest version

```bash
helm repo update hivemq
```

## Install the HiveMQ Platform Operator

This will install a Hivemq Platform Operator for a Kubernetes cluster.

```
helm upgrade --install hivemq-platform-operator hivemq/hivemq-platform-operator
```

For more information on configuring a cluster and advanced usage, visit
the [HiveMQ Operator documentation]
(https://docs.hivemq.com/operator/4.19/kubernetes-operator/platform-operator-for-k8s)

## Install the HiveMQ Platform

This will install a Hivemq Platform with two nodes for evaluation.

For more information on configuring a cluster and advanced usage, visit
the [HiveMQ Operator documentation]
(https://docs.hivemq.com/operator/4.19/kubernetes-operator/platform-operator-for-k8s)

```
helm upgrade --install hivemq-platform hivemq/hivemq-platform
```

## Install the HiveMQ operator

This will install the HiveMQ operator as well as a custom resource that deploys a small 3-node evaluation cluster by
default.

```
helm upgrade --install hivemq hivemq/hivemq-operator
```

For more information on configuring a cluster and advanced usage, visit
the [HiveMQ Operator documentation](https://www.hivemq.com/docs/operator/).

## Install HiveMQ Swarm

This will install HiveMQ with one commander and three agents by default.

```
helm upgrade --install swarm hivemq/hivemq-swarm
```

For more information on configuring HiveMQ Swarm and advanced usage,
visit [HiveMQ Swarm documentation](https://www.hivemq.com/docs/swarm/).
