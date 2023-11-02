# HiveMQ Helm Repository

HiveMQ is a world-class, enterprise-ready MQTT platform that provides fast, efficient, and reliable movement of data to and from connected IoT devices. For more information, visit [HiveMQ](https://www.hivemq.com)

## Add the HiveMQ Helm repository

```
helm repo add hivemq https://hivemq.github.io/helm-charts
```

### In case the repository already exists, upgrade to the latest version

```bash
helm repo update hivemq
```

## Install the HiveMQ Platform Operator for Kubernetes

This will install the Hivemq Platform Operator for Kubernetes with support for HiveMQ versions 4.19 and above.

```
helm upgrade --install hivemq-platform-operator hivemq/hivemq-platform-operator
```

For more information on configuring a cluster and advanced usage, visit
the [HiveMQ Platform Operator documentation](https://docs.hivemq.com/operator/latest/kubernetes-operator/platform-operator-for-k8s)

## Install the HiveMQ Platform

This will install the Hivemq Platform with support for HiveMQ version 4.19 and above. This Helm chart requires the installation of the HiveMQ Platform Operator.

For more information on configuring a HiveMQ Platform and advanced usage, visit
the [HiveMQ Platform Operator documentation](https://docs.hivemq.com/operator/latest/kubernetes-operator/platform-operator-for-k8s)

```
helm upgrade --install hivemq-platform hivemq/hivemq-platform
```

## Install the HiveMQ operator

This will install the (legacy) HiveMQ operator and an example HiveMQ cluster with support for HiveMQ LTS version 4.9 and up.

```
helm upgrade --install hivemq hivemq/hivemq-operator
```

For more information on configuring a cluster and advanced usage, visit
the [HiveMQ Operator documentation](https://docs.hivemq.com/operator/latest/kubernetes-operator/hivemq-kubernetes-operator). 

## Install HiveMQ Swarm

This will install HiveMQ Swarm with one commander and three agents by default.

```
helm upgrade --install swarm hivemq/hivemq-swarm
```

For more information on configuring HiveMQ Swarm and advanced usage,
visit [HiveMQ Swarm documentation](https://docs.hivemq.com/swarm/).
