#!/usr/bin/env sh
set -e

# check if binaries are installed
IS_HELM_INSTALLED=$(which helm >/dev/null 2>&1 || echo "Helm is not installed")
if [ -n "$IS_HELM_INSTALLED" ]; then
  echo "$IS_HELM_INSTALLED"
  exit 1
fi

CHARTS="hivemq-edge hivemq-operator hivemq-platform hivemq-platform-operator hivemq-swarm"
for CHART in ${CHARTS}; do
  helm unittest "./charts/$CHART" -f ./tests/**/*_test.yaml
done
