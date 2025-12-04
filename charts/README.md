# HiveMQ Helm Charts

The HiveMQ Helm Charts contain the necessary resources to deploy the respective HiveMQ application on Kubernetes. 

## Available Charts

### [hivemq-platform-operator](./hivemq-platform-operator)

This chart deploys the HiveMQ Platform Operator that will install and manage your HiveMQ Platform clusters in Kubernetes.

### [hivemq-platform](./hivemq-platform)

This chart specifies the HiveMQ Platform deployment that the HiveMQ Platform Operator then installs.

### [hivemq-operator](./hivemq-operator)

This chart deploys the legacy HiveMQ Operator and a HiveMQ cluster.
The legacy HiveMQ Operator and its Helm chart were retired in April 2025 and no longer receive updates or support.
To migrate to the current HiveMQ Platform Operator for Kubernetes, see [HiveMQ Legacy Operator to Platform Operator Migration Guide](https://docs.hivemq.com/hivemq-operator/migration-guide.html).

### [hivemq-swarm](./hivemq-swarm)

This chart deploys a HiveMQ Swarm cluster, an advanced IoT testing and simulation tool.

### [hivemq-edge](./hivemq-edge)

This chart deploys HiveMQ Edge, a lightweight edge solution for industrial IoT.

## Usage

To deploy a chart, navigate to the respective chart directory and follow the instructions in the corresponding `README.md` file.

## Documentation

See the [HiveMQ Kubernetes documentation](https://docs.hivemq.com/hivemq-platform-operator/introduction.html) for more detailed information.
