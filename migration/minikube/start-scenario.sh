#!/usr/bin/env bash

NAMESPACE=migration
HELM_CHARTS_URL=https://hivemq.github.io/helm-charts
WAIT_TIMEOUT_IN_SECONDS=120
RELEASE_NAME=hivemq
PLATFORM_OPERATOR_RELEASE_NAME=platform-operator

./minikube-start.sh

hivemq_repo=$(helm repo list -o json | jq -r '.[] | select(.url == "'${HELM_CHARTS_URL}'") | .name')
if [ -z "$hivemq_repo" ]; then
  hivemq_repo=hivemq
  helm repo add ${hivemq_repo} https://hivemq.github.io/helm-charts
fi
helm repo update ${hivemq_repo}

echo "Deploying Legacy Operator..."
helm upgrade --install -f ./legacy/values.yml ${RELEASE_NAME} ${hivemq_repo}/hivemq-operator -n ${NAMESPACE} --wait --atomic --create-namespace

isPodReady=$(kubectl get pod ${RELEASE_NAME}-0 -n ${NAMESPACE} --output=jsonpath='{.status.conditions[?(@.type=="Ready")].status}')
if [ "$isPodReady" != "True" ]; then
  start_time=$(date +%s)
  current_time=$(date +%s)
  elapsed_time=$((current_time - start_time))
  printf "%s-0 pod is not ready yet. Waiting for it.." ${RELEASE_NAME}
  while [[ "$isPodReady" != "True" && "$elapsed_time" -le "$WAIT_TIMEOUT_IN_SECONDS" ]]; do
    # Sleep for a short duration before checking again
    printf "."
    sleep 5
    isPodReady=$(kubectl get pod ${RELEASE_NAME}-0 -n ${NAMESPACE} --output=jsonpath='{.status.conditions[?(@.type=="Ready")].status}')
    current_time=$(date +%s)
    elapsed_time=$((current_time - start_time))
  done
  printf "\n"
  if [ "$elapsed_time" -ge "$WAIT_TIMEOUT_IN_SECONDS" ]; then
    echo "Timeout exceeded while waiting for Pods to be ready."
    echo "Uninstalling release..."
    helm uninstall ${RELEASE_NAME} -n ${NAMESPACE}
    exit 1
  fi
fi
echo "HiveMQ Cluster is already available"

nohup kubectl port-forward services/hivemq-hivemq-mqtt 1883:1883 -n ${NAMESPACE} > /dev/null 2>&1 &
portForwardPid=$!
open -n -a Terminal ./mqtt-test.sh

# Scaling down to zero the Legacy Operator
# We need to stop the Legacy operator. Otherwise, it will try to recreate the services for the deleted custom resource.
# By doing so, it will add back the `ownerReference` field which no longer exist and hence K8s will garbage collect the
# services.
echo "Stopping Legacy Operator (scaling down to zero)"
kubectl scale deployment ${RELEASE_NAME}-hivemq-operator-operator --replicas=0 -n ${NAMESPACE}
isOperatorDeleted=$(kubectl get pods -n migration | grep -E '^'${RELEASE_NAME}'-hivemq-operator-operator-')
start_time=$(date +%s)
current_time=$(date +%s)
elapsed_time=$((current_time - start_time))
printf "Legacy Operator is not stopped yet. Waiting for it.."
while [[ -n "$isOperatorDeleted" && "$elapsed_time" -le "$WAIT_TIMEOUT_IN_SECONDS" ]]; do
   # Sleep for a short duration before checking again
  printf "."
  sleep 5
  isOperatorDeleted=$(kubectl get pods -n ${NAMESPACE} | grep -E '^'${RELEASE_NAME}'-hivemq-operator-operator-')
  current_time=$(date +%s)
  elapsed_time=$((current_time - start_time))
done
printf "\n"
if [ "$elapsed_time" -ge "$WAIT_TIMEOUT_IN_SECONDS" ]; then
  echo "Timeout exceeded while waiting for Legacy Operator to be stopped."
  echo "Uninstalling release..."
  helm uninstall ${RELEASE_NAME} -n ${NAMESPACE}
  exit 1
fi
echo "HiveMQ Legacy Operator is already stopped"

echo "Deleting old CR with cascade=orphan..."
kubectl delete hivemq-clusters.hivemq.com ${RELEASE_NAME} --cascade=orphan -n ${NAMESPACE}

# Scaling up the Legacy Operator
echo "Starting HiveMQ Legacy Operator"
kubectl scale deployment ${RELEASE_NAME}-hivemq-operator-operator --replicas=1 -n ${NAMESPACE}

./migrate-resources.sh

#echo "Deploying new Platform Operator..."
#helm upgrade --install -f ./platform/operator-values.yml ${PLATFORM_OPERATOR_RELEASE_NAME} ${hivemq_repo}/hivemq-platform-operator -n ${NAMESPACE} --wait --atomic

## Removing Helm Charts release with --cascade=orphan?
## or should we go ahead and uninstall the release completely? Only the legacy operator is left.
#helm uninstall ${RELEASE_NAME} -n ${NAMESPACE} --wait --cascade=orphan

#sh ./patch-dependent-resources.sh

#helm install -f /Users/antonio.alhambra/Projects/hivemq-platform-helm-charts/migration/minikube/operator-values.yml operator-v2 /Users/antonio.alhambra/Projects/hivemq-platform-helm-charts/hivemq-platform-operator -n ${NAMESPACE} --wait --atomic

#mqtt sub -h localhost -p 1883 -t migration-topic -J
#kill "$portForwardPid"

# helm upgrade -f /Users/antonio.alhambra/Projects/hivemq-platform-helm-charts/migration/minikube/platform-values.yml --install platform /Users/antonio.alhambra/Projects/hivemq-platform-helm-charts/hivemq-platform --wait --atomic
#sh ./clean-up-scenario.sh
