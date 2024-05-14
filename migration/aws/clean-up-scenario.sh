#!/usr/bin/env bash

NAMESPACE=migration
RELEASE_NAME=hivemq
PLATFORM_OPERATOR_RELEASE_NAME=platform-operator
WAIT_TIMEOUT_IN_SECONDS=60

helm uninstall ${PLATFORM_OPERATOR_RELEASE_NAME} -n ${NAMESPACE}
helm uninstall ${RELEASE_NAME} -n ${NAMESPACE}
kubectl delete sts --all -n ${NAMESPACE}
kubectl delete deployments --all -n ${NAMESPACE}
arePodsDeleted=$(kubectl get pods -n migration)
start_time=$(date +%s)
current_time=$(date +%s)
elapsed_time=$((current_time - start_time))
printf "Waiting for all pods to be deleted..."
while [[ -n "$arePodsDeleted" && "$elapsed_time" -le "$WAIT_TIMEOUT_IN_SECONDS" ]]; do
   # Sleep for a short duration before checking again
  printf "."
  sleep 10
  arePodsDeleted=$(kubectl get pods -n ${NAMESPACE})
  current_time=$(date +%s)
  elapsed_time=$((current_time - start_time))
done
printf "\n"
if [ "$elapsed_time" -ge "$WAIT_TIMEOUT_IN_SECONDS" ]; then
  echo "Timeout exceeded while waiting for pods to be deleted."
  exit 1
fi
kubectl delete services --all -n ${NAMESPACE}
kubectl delete cm --all -n ${NAMESPACE}
kubectl delete secrets --all -n ${NAMESPACE}
kubectl delete sa --all -n ${NAMESPACE}
# Since the operator might not be managing the CR, we have to remove the `finalizers` from it manually first
kubectl patch hivemq-platforms.hivemq.com ${RELEASE_NAME} -p '{"metadata":{"finalizers":[]}}' --type=merge -n ${NAMESPACE}
kubectl delete hivemq-platforms.hivemq.com ${RELEASE_NAME} -n ${NAMESPACE}
kubectl delete crd hivemq-clusters.hivemq.com
kubectl delete crd hivemq-platforms.hivemq.com
