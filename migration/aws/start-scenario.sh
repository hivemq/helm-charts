#!/usr/bin/env bash

NAMESPACE=migration
HELM_CHARTS_URL=https://hivemq.github.io/helm-charts
WAIT_TIMEOUT_IN_SECONDS=180
RELEASE_NAME=hivemq
PLATFORM_OPERATOR_RELEASE_NAME=platform-operator

./set-kubeconfig.sh
read -r -n 1 -p "Do you want to build and load local HiveMQ Platform Operator images? (y/n): " choice
choice=$(echo "$choice" | tr '[:upper:]' '[:lower:]')
if [ "$choice" == "y" ]; then
  ./load-images.sh
  printf "\n\nEnvironment setup and container images already loaded\n"
  printf "Press any key to deploy a Legacy Operator with a HiveMQ Cluster...\n"
  read -n 1 -s -r
else
  printf "\n\nEnvironment setup and container images already loaded\n"
fi

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
printf "\nHiveMQ Cluster and Legacy Operator are already up and running\n"
printf "Press any key to delete custom resource with cascade=orphan flag enabled...\n"
read -n 1 -s -r

kubectl patch svc hivemq-${RELEASE_NAME}-mqtt -n ${NAMESPACE} --type='json' --patch='[{"op":"add","path":"/spec/type","value":"NodePort"}]'
nohup kubectl port-forward services/hivemq-hivemq-mqtt 1883:1883 -n ${NAMESPACE} > /dev/null 2>&1 &
portForwardPid=$!
open -n -a Terminal ./mqtt-pub-test.sh

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

printf "\nHiveMQ Cluster '%s' is no longer handled by the Legacy Operator\n" ${RELEASE_NAME}
printf "Press any key to start migrating existing resources (new ConfigMap, new corresponding CustomResource, patching and labeling services, etc) ...\n"
read -n 1 -s -r

./migrate-resources.sh
printf "\nExisting resources are ready for take over\n"
printf "Press any key to deploy new Platform Operator...\n"
read -n 1 -s -r

echo "Deploying new Platform Operator..."
helm upgrade --install -f ./platform/operator-values.yml ${PLATFORM_OPERATOR_RELEASE_NAME} ${hivemq_repo}/hivemq-platform-operator -n ${NAMESPACE} --wait --atomic

printf "\nPlatform Operator deployed, it should take over existing resources\n"
printf "Press any key to verify and test the migration...\n"
read -n 1 -s -r

# We need to re-enable port forwarding as the TCP connection should've been closed due to pod being rolling restarted
kill "$portForwardPid"
nohup kubectl port-forward services/hivemq-hivemq-mqtt 1883:1883 -n ${NAMESPACE} > /dev/null 2>&1 &
open -n -a Terminal ./mqtt-sub-test.sh

printf "Press any key to stop and clean up the migration scenario...\n"
read -n 1 -s -r

./clean-up-scenario.sh

## Removing Helm Charts release with --cascade=orphan?
## or should we go ahead and uninstall the release completely? Only the legacy operator is left.
#helm uninstall ${RELEASE_NAME} -n ${NAMESPACE} --wait --cascade=orphan
