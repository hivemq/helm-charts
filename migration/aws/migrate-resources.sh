#!/usr/bin/env bash

NAMESPACE=migration
RELEASE_NAME=hivemq

# For customers using Helm Charts:
# 1. Using a `legacy-mode` flag/value, try to generate the corresponding CustomResource from the same inputs. We are currently missing a few configurations here
# such as VolumeClaimTemplate, PodManagementPolicy. If not possible to customize everything, customer can use `overrideStatefulSet`
# 2. Service names can be rendered in Helm by using legacy naming when the `legacy-mode` flag is set.
# 3. Existing ConfigMap should be referenced in the values YAML file.
# Otherwise, there will be no Helm release for the HiveMQ Platform chart after the migration.

echo "Applying a new ConfigMap for the HiveMQ config.xml file"
kubectl apply -f ./platform/configmap.yaml -n ${NAMESPACE}
echo "Applying the new CustomResourceDefinition"
kubectl apply -f ../../charts/hivemq-platform-operator/crds/hivemq-platforms.hivemq.com-v1.yml -n ${NAMESPACE}

kubectl apply -f ./platform/customresource.yaml -n ${NAMESPACE}
# Workaround for the status field:
# Issue: https://github.com/kubernetes/kubectl/issues/564
# PR: https://github.com/kubernetes/kubernetes/pull/99556
# https://github.com/kubernetes/enhancements/tree/master/keps/sig-cli/2590-kubectl-subresource

## ANOTHER IMPORTANT THING I NOTICED IS THAT WHEN A ROLLING RESTART IS GOING ON, THE SURGE POD GOES DOWN BEFORE
## THE REST OF THE PODS ARE COMPLETELY UP AND RUNNING (i.e. HiveMQ is not fully initialized and thus not able to serve
## requests)

kubectl patch hivemq-platforms.hivemq.com ${RELEASE_NAME} -n ${NAMESPACE} --subresource=status --type='merge' -p='
{
  "status": {
    "crdVersion": "V1_3_0",
    "message": "HiveMQ Platform is ready",
    "observedGeneration": 1,
    "recoveryInformation": {
      "message": "HiveMQ Platform is starting",
      "state": "STARTING",
      "statePhase": "READY"
    },
    "restartExtensions": "",
    "state": "RUNNING",
    "statePhase": "READY"
  }
}'

# 1. Labeling:
# We need to include the labels:
#   - `hivemq-platform: ${RELEASE_NAME}` to all services (including the platform service or only to the cluster service?).
#   - `hivemq/platform-service: true` to all platform services as these are used to fetch the secondary resources belonging to the custom resource..
# Otherwise these services won't be recognized as an existing service by the operator and the operator will try to create a new one. Also for the reconciliation loop
# as the desired target may different, and the service may get dropped and recreated again.
# 2. Patching:
# We need to patch the services and the statefulset resources to include the reference to the newly created custom resource UID.
# So all of them are linked together with the right custom resource.
uuid="$(kubectl get hivemq-platforms.hivemq.com ${RELEASE_NAME} -o yaml -n ${NAMESPACE} | grep "uid:" | awk '{print $2}')"
echo "Labeling and patching dependent resources for HiveMQ Platform \"hivemq\" with uid $uuid"

kubectl label statefulset ${RELEASE_NAME} -n ${NAMESPACE} --overwrite=true \
  hivemq-platform=${RELEASE_NAME}
kubectl patch statefulset ${RELEASE_NAME} -n ${NAMESPACE}  \
  --type=json -p="[{\"op\": \"add\", \"path\": \"/metadata/ownerReferences\", \"value\": [{\"apiVersion\": \"hivemq.com/v1beta1\", \"kind\": \"HiveMQPlatform\", \"name\": \"${RELEASE_NAME}\", \"uid\": \"$uuid\"}]}]"

kubectl label svc hivemq-${RELEASE_NAME}-cc -n ${NAMESPACE} --overwrite=true \
  hivemq-platform=${RELEASE_NAME} hivemq/platform-service=true
kubectl patch svc hivemq-${RELEASE_NAME}-cc -n ${NAMESPACE}  \
  --type=json -p="[{\"op\": \"add\", \"path\": \"/metadata/ownerReferences\", \"value\": [{\"apiVersion\": \"hivemq.com/v1beta1\", \"kind\": \"HiveMQPlatform\", \"name\": \"${RELEASE_NAME}\", \"uid\": \"$uuid\"}]}]"

kubectl label svc hivemq-${RELEASE_NAME}-mqtt -n ${NAMESPACE} --overwrite=true \
  hivemq-platform=${RELEASE_NAME} hivemq/platform-service=true
kubectl patch svc hivemq-${RELEASE_NAME}-mqtt -n ${NAMESPACE}  \
  --type=json -p="[{\"op\": \"add\", \"path\": \"/metadata/ownerReferences\", \"value\": [{\"apiVersion\": \"hivemq.com/v1beta1\", \"kind\": \"HiveMQPlatform\", \"name\": \"${RELEASE_NAME}\", \"uid\": \"$uuid\"}]}]"

kubectl label svc hivemq-${RELEASE_NAME}-cluster -n ${NAMESPACE} --overwrite=true \
  hivemq-platform=${RELEASE_NAME}
kubectl patch svc hivemq-${RELEASE_NAME}-cluster -n ${NAMESPACE} \
  --type=json -p="[{\"op\": \"add\", \"path\": \"/metadata/ownerReferences\", \"value\": [{\"apiVersion\": \"hivemq.com/v1beta1\", \"kind\": \"HiveMQPlatform\", \"name\": \"${RELEASE_NAME}\", \"uid\": \"$uuid\"}]}]"
