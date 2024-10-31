#!/bin/sh
set -e

HELM_CHARTS_BASE_FOLDER="$(cd -- "$(dirname -- "$0")/../.." && pwd)"
. "${HELM_CHARTS_BASE_FOLDER}/manifests/manifests-common.sh"

export LABEL="HiveMQ Swarm"
export CHART_NAME="hivemq-swarm"
export RELEASE_NAME="${1:-my-swarm}"
export HELM_CHARTS_BASE_FOLDER
export PROMETHEUS_HELM_CHARTS_URL=https://prometheus-community.github.io/helm-charts

update_manifests
