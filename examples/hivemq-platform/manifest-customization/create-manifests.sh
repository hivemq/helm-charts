#!/bin/sh

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"

# configuration
export LABEL="HiveMQ Platform"
export CHART_NAME="hivemq-platform"
export RELEASE_NAME="test-platform"
export HELM_CHARTS_BASE_FOLDER="${SCRIPT_DIR}/../../.."
export TARGET_FOLDER="${SCRIPT_DIR}/deployment"
export HELM_VALUES="${SCRIPT_DIR}/values.yaml"

# create manifest files
. "${HELM_CHARTS_BASE_FOLDER}/manifests/manifests-common.sh"
update_manifests
