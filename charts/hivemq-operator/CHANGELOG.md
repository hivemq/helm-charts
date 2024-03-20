# Changelog

## [0.12.0](https://github.com/afalhambra-hivemq/helm-charts/compare/hivemq-operator-0.11.34...hivemq-operator-v0.12.0) (2024-03-20)


### Features

* cross namespace mode ([#57](https://github.com/afalhambra-hivemq/helm-charts/issues/57)) ([e87bd62](https://github.com/afalhambra-hivemq/helm-charts/commit/e87bd626dab36782847a11350f00f9ca6a45511c))


### Bug Fixes

* add missing volumeClaimTemplates field in CRD template ([0afb89d](https://github.com/afalhambra-hivemq/helm-charts/commit/0afb89de397198faf5ec1d2e5f74d8e20b221077))
* allow configuration of securityContext for admission webhooks ([4ab42cf](https://github.com/afalhambra-hivemq/helm-charts/commit/4ab42cf1b8b97d460639893cfdf30bcb4e66097e))
* allow operator to update finalizers ([a74d39f](https://github.com/afalhambra-hivemq/helm-charts/commit/a74d39f1bc54a35ab3f0f361acf4f8d2107b20d7))
* allow to specify initBusyboxImage and initDnsWaitImage for default hivemq cluster ([e5d2dde](https://github.com/afalhambra-hivemq/helm-charts/commit/e5d2dde8f4debcbe9702c3d461bf82252453b489))
* change webhook for the upstream nginx version ([d6a8617](https://github.com/afalhambra-hivemq/helm-charts/commit/d6a861734ab9aae281ed224dc7311465f6699a5b))
* default configuration to enable overload protection ([5013cff](https://github.com/afalhambra-hivemq/helm-charts/commit/5013cff9c98332134237b1eafd50a7485965df8a))
* make volumeClaimTemplates field more consistent with remaining custom resource default values ([4f0d8ce](https://github.com/afalhambra-hivemq/helm-charts/commit/4f0d8cef92c31625e3b0845c7388784efc4ab7ea))
* render podLabels in the custom resource template ([28bfc82](https://github.com/afalhambra-hivemq/helm-charts/commit/28bfc82742150f772cb9df23443db4173e2d11c7))
* The default rest-api is disabled ([3e68dd9](https://github.com/afalhambra-hivemq/helm-charts/commit/3e68dd9814a9c5c26b407850e0838016c7503c1c))
* update to correct config.xml override template, fix some PSP errors, fix operator cluster role permissions for PVC + event objects ([b5eb31e](https://github.com/afalhambra-hivemq/helm-charts/commit/b5eb31e5ca1ba2933f2176bda1d4294cbb73a356))
* update to latest controller templates, latest v1 CRD, add user info for legacy CRD support, update generated manifests, add new field defaults ([595ec2f](https://github.com/afalhambra-hivemq/helm-charts/commit/595ec2fcfe6df4a995849e875cd9d4a88e98b19b))
* Upgrade kube-prometheus-stack to version: 30.* ([#94](https://github.com/afalhambra-hivemq/helm-charts/issues/94)) ([300a61e](https://github.com/afalhambra-hivemq/helm-charts/commit/300a61e23adcda7bcccfb50bfec410fb231d679f))
* version number ([4d71eb6](https://github.com/afalhambra-hivemq/helm-charts/commit/4d71eb62580c63a3d6ad77468c76ca1d0764a880))


### Miscellaneous Chores

* bump chart version + add changelog ([62b3e58](https://github.com/afalhambra-hivemq/helm-charts/commit/62b3e589c6d02be6bd82e86d13389011ef638163))
* bump chart version to reflect template change ([b687ccc](https://github.com/afalhambra-hivemq/helm-charts/commit/b687cccabf4e01906349060ab439a74b794e5f2a))
* update image versions, bump chart version, add changelog ([b292372](https://github.com/afalhambra-hivemq/helm-charts/commit/b292372f3f6b092f7fe9d8be6cfa6f3dc8285ee8))

## chart 0.11.23

- Remove extension configmap override on the initialization script, as this is already done by the operator

## chart 0.11.22

- Disable pod security policy by default because it is removed starting with K8s = v1.25

## chart 0.11.16

- Update usage of the legacy k8s.gcr.io container image registry to registry.k8s.io.
  See: https://kubernetes.io/blog/2023/03/10/image-registry-redirect/
- Add support to test k8s > 1.24

## chart 0.11.14

- Bump operator to 4.7.10
- Created a Prometheus service monitor Custom Resource when monitoring.enabled and monitoring.dedicated set to true

## chart 0.11.10

- Bump operator to 4.7.9
- Prepare for HiveMQ release 4.10

## chart 0.11.6

- Update to Prometheus 14
- Multi-namespace prometheus
- Using G1 Garbage collector for prometheus
- Test using K3s v1.24.3

## chart 0.11.5

- Use 4.8.4 HiveMQ Release

## chart 0.11.4

- Increase container limit and request size to 640MB
- Allow to customize the java environment and set the default memory usage to 75%

## chart 0.11.3

- Updates operator to 4.7.5
- Clean up the CRD, removing some unnecessary generated fields that get always pruned by kube-api

## chart 0.11.1

- Update to latest operator version 4.7.3

## chart 0.11.0 - HiveMQ platform 4.8.0

- Added ability for the operator to orchestrate HiveMQCluster resources across all
  namespaces. (`operator.crossNamespaceMode`)

## chart 0.10.12

- Document how to create and use non-root security context

## chart 0.10.11

- Upgrade to latest HiveMQ release 4.8.3

## chart 0.10.10

- Upgrade to latest HiveMQ release 4.8.2

## chart 0.10.09

- Local images tests
- Matrix tests
- Security rootless images tests

## chart 0.10.5

- Added ability to pass arbitrary environment variables to the operator pod
- Added ability to specify `affinity` for the operator

## chart 0.10.3

- Fixed blocking deployment on version 1.22 using a newer cert-gen webhook from nginx
- Match versions of the chart.yaml and changelog

## chart 0.9.3

- Fixed templating of the `queuedMessagesMaxQueueSize` field in the custom resource

## chart 0.9.2

- Fix an issue where the operator would not properly sync the `-dynamic-state` ConfigMap

## chart 0.9.1

- Fix overload protection configuration
- Add missing RBAC configuration, so that the chart is compatible with Openshift
- Allow customization of init images in values.yaml
- Allow security context configuration for webhook job

## 4.7.0 (chart 0.9.0)

The most significant changes in this release are:

- The CRD update (You must update your CustomResourceDefinition. Can be done without any downtime, simply apply the new
  CRD
  using `kubectl apply -f https://raw.githubusercontent.com/hivemq/helm-charts/master/charts/hivemq-operator/crds/hivemq-cluster.yaml`)
- The Helm Chart now uses v1 CustomResourceDefinition by default. This is only supported in Kubernetes 1.16+. If you are
  running Kubernetes <1.16, Please apply the CRD directly
  using `kubectl apply -f https://raw.githubusercontent.com/hivemq/helm-charts/master/manifests/legacy/v1beta1-hivemq-cluster.yaml`.
- The operator now supports the stable v1 api version of CustomResourceDefinition.
- HiveMQ now runs with a more restricted Pod & container security context by default. Additionally, security context
  configurations are now customizable.

Full list of changes:

- Fix the `customProperties` field in the CustomResourceDefinition. It was previously not working properly due to
  field-pruning. Note that customProperties must be string-types now.
- The CustomResourceDefinition of this version contains some changes. While the changes are non-breaking, be aware that
  you must update the CRD on your cluster.
- Added various fields to the CustomResourceDefinition and added them to the templates, to ensure most fields of the
  underlying pod & controller specs can be utilized.
    - `sidecars`: Add arbitrary containers to the HiveMQ Pod
    - `initContainers`: Add arbitrary initContainers to the HiveMQ Pod (Similar to `initialization` but with more
      flexibility)
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
- StatefulSet support is now considered GA-ready. (The default type of Deployment has not changed yet, though.) Notable
  improvements:
    - The operator now encodes operations logic for properly orchestrating HiveMQ on a StatefulSet.
      The operator will ensure an additional (surge) HiveMQ node with the updated configuration will get spawned prior
      to rolling-updating the cluster.
      An OnDelete strategy is required in the StatefulSet template to facilitate this, as the operator will also ensure
      a proper cool-down period between individual nodes being restarted.
- The Helm Chart (and operator) now use the V1 CustomResourceDefinition api version. v1beta1 is still supported. See
  above.
- Fixed a bug where specifically formatted extensionUris would lead to broken extension state management

## chart 0.8.5

- Fix heap dump path environment variable. Heap dumps are now properly saved to `/opt/hivemq/dumps`.

## chart 0.8.4

- Fix custom env templating by using correct YAML block scalar string syntax

## chart 0.8.3

- Regular version bump through CI

## chart 0.8.2

- Regular version bump through CI

## chart 0.8.1

- fix templating of the image pull secrets into the HiveMQ ServiceAccount

## 4.5.0 (chart 0.8.0)

- BREAKING: Changed default transport type to TCP. Note that if you are upgrading, you must set the transport explicitly
  to UDP or re-create (backup, uninstall, install) your cluster to switch to TCP transport.
- Reduce `cpuLimitRatio` default value to 1 for a more predictable CPU count seen by HiveMQ.
- Added safe sysctl settings by default to the HiveMQ Pods' security context which will extend the local port range
- Add proper templating and default example for the initialization field.
- Add nodeSelector support to operator deployment template
- Increased liveness `failureThreshold` to ensure joining HiveMQ nodes don't get shut down during a long-lasting join
  process
- Fix templating of multi-line environment variables
- Add a default heap dump storage path and volume, to preserve heap dump files after container restarts (requires HiveMQ
  4.4.3+)
- Fix image pull secrets not being used in generated custom resource
- Add field for adding annotations to the operator service account
- Improve service monitor naming to exactly match the generated cluster name, for easier correlation when querying
  metrics
- Migrate validation hook TLS provisioning to webhook cert generator to make validation hook configuration more reliable
- Move image pull secrets to global section
- Introduce more detailed webhook configuration options
- Add namespace override
