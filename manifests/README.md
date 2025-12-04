# Manifests

The Manifest files are generated with the Helm chart default values.
Manifest files serve as templates that can be customized for manual deployment with `kubectl`.

### [hivemq-platform-operator](./hivemq-platform-operator)
This folder contains the manifest files to deploy the HiveMQ Platform Operator.

### [hivemq-platform](./hivemq-platform)
This folder contains the manifest files to deploy the HiveMQ Platform.

### [hivemq-operator](./hivemq-operator)
This folder contains the manifest files to deploy the HiveMQ Operator (legacy).
The legacy HiveMQ Operator, its Helm chart and manifest files were retired in April 2025 and no longer receive updates or support.
To migrate to the current HiveMQ Platform Operator for Kubernetes, see [HiveMQ Legacy Operator to Platform Operator Migration Guide](https://docs.hivemq.com/hivemq-operator/migration-guide.html).

### [hivemq-swarm](./hivemq-swarm)
This folder contains the manifest files to deploy HiveMQ Swarm.

## Documentation
See the [HiveMQ Kubernetes documentation](https://docs.hivemq.com/hivemq-platform-operator/introduction.html) for more detailed information.
