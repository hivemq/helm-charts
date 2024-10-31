#!/bin/sh
set -e

HELM_CHARTS_BASE_FOLDER="$(cd -- "$(dirname -- "$0")/../.." && pwd)"
. "${HELM_CHARTS_BASE_FOLDER}/manifests/manifests-common.sh"

export LABEL="HiveMQ Platform"
export CHART_NAME="hivemq-platform"
export RELEASE_NAME="${1:-my-platform}"
export HELM_CHARTS_BASE_FOLDER

update_manifests
