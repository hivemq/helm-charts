#!/usr/bin/env bash

echo "Are we ready yet?"
# A pretty hacky readiness probe until we have a proper API in HiveMQ

TARGET_SIZE=$(cat /opt/hivemq/initial_node_count)

# Once we're ready we will always be for the remainder of the lifetime of the pod
if [[ -f ready_file ]]; then
  exit 0
fi


HAS_STARTED=$(tail -n1000 /opt/hivemq/log/hivemq.log | grep 'Started HiveMQ in' | tail -n1 | wc -l)
if [[ "${TARGET_SIZE}" == 1 ]]; then
  if (( HAS_STARTED == 1 )); then
    touch ready_file
    exit 0
  fi
fi

CLUSTER_SIZE=$(tail -n1000 /opt/hivemq/log/hivemq.log | grep 'Cluster size' | sed -Ee 's/.*size = ([0-9]+).*/\1/' | tail -n1)

# Log not found
if [[ -z ${CLUSTER_SIZE} ]]; then
  echo "Log statement not present (yet)"
  exit 2
fi

if (( CLUSTER_SIZE >= TARGET_SIZE && HAS_STARTED == 1 )); then
  touch ready_file
  exit 0
fi


# Default fail
echo "Cluster size not matched. Current: ${CLUSTER_SIZE}, expected: ${TARGET_SIZE}"
exit 1