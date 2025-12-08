#!/usr/bin/env bash
set -e

if [ "$#" -ne 3 ]; then
    echo "Usage: wait-for-pods.sh <APP_NAME> <APP_LABEL> <NAMESPACE>"
    echo ""
    echo "Example: wait-for-pods.sh hivemq-platform app.kubernetes.io/instance=my-app test"
    return 1
fi

# function to wait until a specified pod is running and ready
# shellcheck disable=SC2155
wait_for_pods() {
  local APP_NAME=$1
  local APP_LABEL=$2
  local NAMESPACE=$3
  local TIMEOUT=120  # 2 minutes timeout

  # wait until the specified pods are started
  local pod_start_time=$(date +%s)
  while [[ -z "$(kubectl get pods -l "$APP_LABEL" -n "$NAMESPACE" -o name)" ]]; do
    local current_time=$(date +%s)
    local elapsed=$((current_time - pod_start_time))

    if [[ $elapsed -ge $TIMEOUT ]]; then
      echo "Timeout reached while waiting for pods to start. Exiting with failure."
      exit 1
    fi

    echo "Waiting for $APP_NAME to start..."
    sleep 1
  done

  # wait until the pods are ready
  echo "$APP_NAME pods are started. Checking readiness..."
  local readiness_start_time=$(date +%s)
  until kubectl get pods -n "$NAMESPACE" -l "$APP_LABEL" -o jsonpath='{.items[0].status.containerStatuses[0].ready}' | grep -q "true"; do
    local current_time=$(date +%s)
    local elapsed=$((current_time - readiness_start_time))

    if [[ $elapsed -ge $TIMEOUT ]]; then
      echo "Timeout reached while waiting for pods to become ready. Exiting with failure."
      exit 1
    fi

    echo "Waiting for $APP_NAME pods to become ready..."
    sleep 1
  done
  echo "$APP_NAME pods are ready..."

  echo ""
  echo "=== Pod Status ==="
  kubectl get pods -n "$NAMESPACE" -l "$APP_LABEL" -o wide

  echo ""
  echo "=== Pod Details ==="
  kubectl describe pods -n "$NAMESPACE" -l "$APP_LABEL" | grep -A 10 "Conditions:"

  echo ""
  echo "=== Services ==="
  kubectl get services -n "$NAMESPACE"

  echo ""
  echo "=== Service Endpoints ==="
  kubectl get endpoints -n "$NAMESPACE"

  echo ""
  echo "=== Service Details ==="
  kubectl describe services -n "$NAMESPACE" | grep -E "(Name:|Selector:|Endpoints:|Port:)" || echo "No services found"
}

wait_for_pods "$@"
