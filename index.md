# HiveMQ Helm Repository

## Add the HiveMQ Helm repository

```
helm repo add hivemq https://hivemq.github.io/helm-charts
```

## Install the HiveMQ operator

This will install the HiveMQ operator as well as a custom resource that deploys a small 3-node evaluation cluster by default.

```
helm upgrade --install hivemq hivemq/hivemq-operator
```

For more information on configuring a cluster and advanced usage, visit the [HiveMQ Operator documentation](https://www.hivemq.com/docs/operator/).

## Install HiveMQ Swarm

This will install HiveMQ with one commander and three agents by default.

```
helm upgrade --install swarm hivemq/hivemq-swarm
```

For more information on configuring HiveMQ Swarm and advanced usage, visit [HiveMQ Swarm documentation](https://www.hivemq.com/docs/swarm/).
