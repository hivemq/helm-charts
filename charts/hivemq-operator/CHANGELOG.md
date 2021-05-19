# chart 0.9.3

- Fixed templating of the `queuedMessagesMaxQueueSize` field in the custom resource

# chart 0.9.2

- Fix an issue where the operator would not properly sync the `-dynamic-state` ConfigMap

# chart 0.9.1

- Fix overload protection configuration
- Add missing RBAC configuration, so that the chart is compatible with Openshift
- Allow customization of init images in values.yaml
- Allow security context configuration for webhook job

# 4.7.0 (chart 0.9.0)

The most significant changes in this release are:
- The CRD update (You must update your CustomResourceDefinition. Can be done without any downtime, simply apply the new CRD using `kubectl apply -f https://raw.githubusercontent.com/hivemq/helm-charts/master/charts/hivemq-operator/crds/hivemq-cluster.yaml`)
- The Helm Chart now uses v1 CustomResourceDefinition by default. This is only supported in Kubernetes 1.16+. If you are running Kubernetes <1.16, Please apply the CRD directly using `kubectl apply -f https://raw.githubusercontent.com/hivemq/helm-charts/master/manifests/legacy/v1beta1-hivemq-cluster.yaml`. 
- The operator now supports the stable v1 api version of CustomResourceDefinition.
- HiveMQ now runs with a more restricted Pod & container security context by default. Additionally, security context configurations are now customizable.

Full list of changes:
- Fix the `customProperties` field in the CustomResourceDefinition. It was previously not working properly due to field-pruning. Note that customProperties must be string-types now.
- The CustomResourceDefinition of this version contains some changes. While the changes are non-breaking, be aware that you must update the CRD on your cluster.
- Added various fields to the CustomResourceDefinition and added them to the templates, to ensure most fields of the underlying pod & controller specs can be utilized.
  - `sidecars`: Add arbitrary containers to the HiveMQ Pod
  - `initContainers`: Add arbitrary initContainers to the HiveMQ Pod (Similar to `initialization` but with more flexibility)
  - `podLabels`: Add arbitrary labels to the HiveMQ Pod
  - `podAnnotations`: Add arbitrary annotations to the HiveMQ Pod
  - `nodeSelector`:  Configure a NodeSelector for the HiveMQ Pod
  - `priorityClassName`: Configure a priorityClassName in the underlying controller
  - `runtimeClassName`: Configure a runtimeClassName in the underlying controller
  - `tolerations`: Configure tolerations in the underlying controller
  - `additionalVolumes`: Add arbitrary volumes to the HiveMQ Pod
  - `additionalVolumeMounts`: Add arbitrary volume mounts to the HiveMQ container
  - `topologySpreadConstraints`: Configure topologySpreadConstraints in the underlying controller
  - `dnsSuffix`: Field for configuring a custom `svc.cluster.local` suffix for DNS discovery
  - `operatorHints`: Section for configuring operations logic such as surge node orchestration & PVC clean-up
  - `podSecurityContext`: For providing a custom securityContext for the HiveMQ Pod
  - `volumeClaimTemplates`: (StatefulSet only) for providing volume claim templates to the StatefulSet.
  - `containerSecurityContext`: For providing a custom securityContext for the HiveMQ container
  - mqtt section:
    - Added missing `messageExpiryMaxInterval` field
  - security section:
    - Added missing `controlCenterAuditLogEnabled` field
- Improved the `env` field in the CRD to properly support specifying env vars directly from secret objects.
- StatefulSet support is now considered GA-ready. (The default type of Deployment has not changed yet, though.) Notable improvements:
  - The operator now encodes operations logic for properly orchestrating HiveMQ on a StatefulSet.
    The operator will ensure an additional (surge) HiveMQ node with the updated configuration will get spawned prior to rolling-updating the cluster.
    An OnDelete strategy is required in the StatefulSet template to facilitate this, as the operator will also ensure a proper cool-down period between individual nodes being restarted.
- The Helm Chart (and operator) now use the V1 CustomResourceDefinition api version. v1beta1 is still supported. See above.
- Fixed a bug where specifically formatted extensionUris would lead to broken extension state management

# chart 0.8.5

- Fix heap dump path environment variable. Heap dumps are now properly saved to `/opt/hivemq/dumps`.

# chart 0.8.4

- Fix custom env templating by using correct YAML block scalar string syntax

# chart 0.8.3

- Regular version bump through CI

# chart 0.8.2

- Regular version bump through CI

# chart 0.8.1

- fix templating of the image pull secrets into the HiveMQ ServiceAccount

# 4.5.0 (chart 0.8.0)

- BREAKING: Changed default transport type to TCP. Note that if you are upgrading, you must set the transport explicitly to UDP or re-create (backup, uninstall, install) your cluster to switch to TCP transport.
- Reduce `cpuLimitRatio` default value to 1 for a more predictable CPU count seen by HiveMQ.
- Added safe sysctl settings by default to the HiveMQ Pods' security context which will extend the local port range
- Add proper templating and default example for the initialization field.
- Add nodeSelector support to operator deployment template
- Increased liveness `failureThreshold` to ensure joining HiveMQ nodes don't get shut down during a long-lasting join process
- Fix templating of multi-line environment variables
- Add a default heap dump storage path and volume, to preserve heap dump files after container restarts (requires HiveMQ 4.4.3+)
- Fix image pull secrets not being used in generated custom resource
- Add field for adding annotations to the operator service account
- Improve service monitor naming to exactly match the generated cluster name, for easier correlation when querying metrics
- Migrate validation hook TLS provisioning to webhook cert generator to make validation hook configuration more reliable
- Move image pull secrets to global section
- Introduce more detailed webhook configuration options
- Add namespace override
