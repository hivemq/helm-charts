#!/usr/bin/env sh
set -e

# check if binaries are installed
IS_HELM_INSTALLED=$(which helm >/dev/null 2>&1 || echo "Helm is not installed")
if [ -n "$IS_HELM_INSTALLED" ]; then
  echo "$IS_HELM_INSTALLED"
  exit 1
fi

# check if the 'unittest' Helm plugin is installed
if ! helm plugin list | grep -q 'unittest'; then
  echo "Helm plugin 'unittest' is not installed"
  exit 1
fi

CHARTS="hivemq-edge hivemq-platform hivemq-platform-operator hivemq-swarm"
CHART_FILTER="$1"

for CHART in ${CHARTS}; do
  if [ -z "$CHART_FILTER" ] || [ "$CHART" = "$CHART_FILTER" ]; then
    helm unittest "./charts/$CHART" -f ./tests/**/*_test.yaml
  fi
done
