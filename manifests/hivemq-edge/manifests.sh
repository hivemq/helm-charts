#!/bin/sh
set -e

HELM_CHARTS_BASE_FOLDER="$(cd -- "$(dirname -- "$0")/../.." && pwd)"
. "${HELM_CHARTS_BASE_FOLDER}/manifests/manifests-common.sh"

export LABEL="HiveMQ Edge"
export CHART_NAME="hivemq-edge"
export RELEASE_NAME="${1:-my-platform}"
export HELM_CHARTS_BASE_FOLDER

update_manifests
