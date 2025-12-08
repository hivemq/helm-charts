#!/usr/bin/env bash
set -e

if [ "$#" -ne 4 ]; then
    echo "Usage: wait-for-log-in-pods.sh <APP_NAME> <APP_LABEL> <NAMESPACE> <LOG_MESSAGE>"
    echo ""
    echo "Example: wait-for-log-in-pods.sh hivemq-edge app.kubernetes.io/instance=my-app test 'Started application'"
    exit 1
fi

# function to wait until a specific log message appears in pod logs
# shellcheck disable=SC2155
wait_for_log_in_pods() {
  local APP_NAME=$1
  local APP_LABEL=$2
  local NAMESPACE=$3
  local LOG_MESSAGE=$4
  local TIMEOUT=120  # 2 minutes timeout
  local MARKER_FILE="/tmp/app-started-marker"

  # wait until the specified pod are started
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
  local pod_name=$(kubectl get pods -n "$NAMESPACE" -l "$APP_LABEL" -o jsonpath='{.items[0].metadata.name}')
  local readiness_start_time=$(date +%s)
  until kubectl get pod "$pod_name" -n "$NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null | grep -q "Running"; do
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
  echo "Checking if $APP_NAME has already started..."

  # check if marker file already exists (from previous startup)
  if kubectl exec "$pod_name" -n "$NAMESPACE" -- sh -c "test -f $MARKER_FILE" 2>/dev/null; then
    echo "✓ $APP_NAME is already started"
  else
    echo "Waiting for log message: '$LOG_MESSAGE'..."

    # wait for the log message to appear
    local log_wait_start_time=$(date +%s)
    local found_startup_message=false

    until [ "$found_startup_message" = true ]; do
      local current_time=$(date +%s)
      local elapsed=$((current_time - log_wait_start_time))

      if [[ $elapsed -ge $TIMEOUT ]]; then
        echo "Timeout reached while waiting for log message. Last 50 lines of logs:"
        kubectl logs "$pod_name" -n "$NAMESPACE" --since=30s --tail=50 2>/dev/null || echo "Could not retrieve logs"
        exit 1
      fi

      # check if the log message is in the logs
      if kubectl logs "$pod_name" -n "$NAMESPACE" --since=30s 2>/dev/null | grep -qF "$LOG_MESSAGE"; then
        echo "✓ Found '$LOG_MESSAGE' message in logs"
        found_startup_message=true

        # create a marker file in the pod to indicate application has started
        if ! kubectl exec "$pod_name" -n "$NAMESPACE" -- sh -c "touch $MARKER_FILE" 2>/dev/null; then
          echo "Warning: Could not create marker file in pod"
        fi
      else
        echo "Not ready yet, waiting... (${elapsed}s elapsed)"
        sleep 3
      fi
    done
  fi
  echo "$APP_NAME is ready..."

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

wait_for_log_in_pods "$@"
