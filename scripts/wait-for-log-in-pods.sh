#!/usr/bin/env bash
set -e

if [ "$#" -ne 3 ]; then
    echo "Usage: wait-for-log-in-pods.sh <APP_LABEL> <NAMESPACE> <LOG_MESSAGE>"
    echo ""
    echo "Example: wait-for-log-in-pods.sh app.kubernetes.io/instance=my-app test 'Started application'"
    exit 1
fi

# Function to wait until a specific log message appears in pod logs
wait_for_log_in_pods() {
  local APP_LABEL=$1
  local NAMESPACE=$2
  local LOG_MESSAGE=$3
  local TIMEOUT=120  # 2 minutes timeout
  local MARKER_FILE="/tmp/app-started-marker"

  echo "Waiting for pods with label '$APP_LABEL' to exist..."
  local start_time=$(date +%s)

  # Wait until the pod exists
  while [[ -z "$(kubectl get pods -l "$APP_LABEL" -n "$NAMESPACE" -o name 2>/dev/null)" ]]; do
    local current_time=$(date +%s)
    local elapsed=$((current_time - start_time))

    if [[ $elapsed -ge $TIMEOUT ]]; then
      echo "Timeout reached while waiting for pods to exist. Exiting with failure."
      exit 1
    fi

    echo "Waiting for pods to be created..."
    sleep 2
  done

  local pod_name=$(kubectl get pods -n "$NAMESPACE" -l "$APP_LABEL" -o jsonpath='{.items[0].metadata.name}')
  echo "Found pod: $pod_name"

  # Wait until the pod is running
  echo "Waiting for pod $pod_name to be in Running state..."
  local start_time=$(date +%s)
  until kubectl get pod "$pod_name" -n "$NAMESPACE" -o jsonpath='{.status.phase}' 2>/dev/null | grep -q "Running"; do
    local current_time=$(date +%s)
    local elapsed=$((current_time - start_time))

    if [[ $elapsed -ge $TIMEOUT ]]; then
      echo "Timeout reached while waiting for pod to be Running. Exiting with failure."
      exit 1
    fi

    echo "Pod not yet running, waiting..."
    sleep 2
  done

  echo "Pod is running. Checking if application has already started..."

  # Check if marker file already exists (from previous startup)
  if kubectl exec "$pod_name" -n "$NAMESPACE" -- sh -c "test -f $MARKER_FILE" 2>/dev/null; then
    echo "✓ Application is already started"
  else
    echo "Waiting for log message: '$LOG_MESSAGE'..."

    # Wait for the log message to appear
    local start_time=$(date +%s)
    local found_startup_message=false

    until [ "$found_startup_message" = true ]; do
      local current_time=$(date +%s)
      local elapsed=$((current_time - start_time))

      if [[ $elapsed -ge $TIMEOUT ]]; then
        echo "Timeout reached while waiting for log message. Last 50 lines of logs:"
        kubectl logs "$pod_name" -n "$NAMESPACE" --tail=50 2>/dev/null || echo "Could not retrieve logs"
        exit 1
      fi

      # Check if the log message is in the logs
      if kubectl logs "$pod_name" -n "$NAMESPACE" 2>/dev/null | grep -qF "$LOG_MESSAGE"; then
        echo "✓ Found '$LOG_MESSAGE' message in logs"
        found_startup_message=true

        # Create a marker file in the pod to indicate application has started
        if ! kubectl exec "$pod_name" -n "$NAMESPACE" -- sh -c "touch $MARKER_FILE" 2>/dev/null; then
          echo "Warning: Could not create marker file in pod"
        fi
      else
        echo "Not ready yet, waiting... (${elapsed}s elapsed)"
        sleep 3
      fi
    done
  fi

  # Debug output: Show pod status
  echo ""
  echo "=== Pod Status ==="
  kubectl get pods -n "$NAMESPACE" -l "$APP_LABEL" -o wide

  # Debug output: Show services
  echo ""
  echo "=== Services ==="
  kubectl get services -n "$NAMESPACE"

  # Debug output: Show service endpoints
  echo ""
  echo "=== Service Endpoints ==="
  kubectl get endpoints -n "$NAMESPACE"
}

wait_for_log_in_pods "$@"
