#!/bin/sh
set -e

# Usage:
# export LABEL="HiveMQ Platform"
# export CHART_NAME="hivemq-platform"
# export RELEASE_NAME="my-release-name"
# export HELM_CHARTS_BASE_FOLDER="${HOME}/github/helm-charts"
# export TARGET_FOLDER="${HOME}/custom/deployment" # optional
# export HELM_VALUES="${HOME}/custom/values.yaml" # optional
# . "${HELM_CHARTS_BASE_FOLDER}/manifests/manifests.sh"
update_manifests() {
  IS_HELM_INSTALLED=$(which helm >/dev/null 2>&1 || echo "Helm is not installed")
  if [ -n "$IS_HELM_INSTALLED" ]; then
    echo "$IS_HELM_INSTALLED"
    exit 1
  fi
  if [ -z "${LABEL}" ]; then
    echo "No label set"
    exit 1
  fi
  if [ -z "${CHART_NAME}" ]; then
    echo "No chart name set"
    exit 1
  fi
  if [ -z "${RELEASE_NAME}" ]; then
    echo "No release name set"
    exit 1
  fi
  if [ -z "${HELM_CHARTS_BASE_FOLDER}" ]; then
    echo "No helm-charts base folder set"
    exit 1
  fi
  if [ -z "${TARGET_FOLDER}" ]; then
    TARGET_FOLDER="${HELM_CHARTS_BASE_FOLDER}/manifests"
  fi

  # variables
  MANIFEST_DIR="${TARGET_FOLDER}/${CHART_NAME}"
  TEMPLATES_DIR="${MANIFEST_DIR}/templates"
  CHART_DIR="${HELM_CHARTS_BASE_FOLDER}/charts/${CHART_NAME}"
  if [ ! -d "${CHART_DIR}" ]; then
    echo "No Helm chart found in ${CHART_DIR}"
    exit 1
  fi
  if [ -z "${HELM_VALUES}" ]; then
    HELM_VALUES="${CHART_DIR}/values.yaml"
  fi
  if [ ! -f "${HELM_VALUES}" ]; then
    echo "No Helm chart values found in ${HELM_VALUES}"
    exit 1
  fi

  echo "================================"
  echo "Generating $LABEL Manifest files"
  echo "================================"

  echo "Create manifests directory"
  mkdir -p "${TARGET_FOLDER}"

  if [ -n "${PROMETHEUS_HELM_CHARTS_URL}" ]; then
    echo "Update ${LABEL} dependencies"
    prometheus_repo=$(helm repo list -o json | jq -r '.[] | select(.url == "'"${PROMETHEUS_HELM_CHARTS_URL}"'") | .name')
    if [ -z "$prometheus_repo" ]; then
      echo "Adding Prometheus Helm Chart dependency"
      helm repo add prometheus "${PROMETHEUS_HELM_CHARTS_URL}"
    fi
    helm dependency build "${CHART_DIR}"
  fi

  echo "Create ${LABEL} templates"
  helm template "${RELEASE_NAME}" "${CHART_DIR}" --values "${HELM_VALUES}" --skip-tests --output-dir "${TARGET_FOLDER}" > /dev/null

  echo "Flatten directory structure"
  find "${TEMPLATES_DIR}" -type f -exec mv {} "${MANIFEST_DIR}" \; > /dev/null
  if [ -d "${TEMPLATES_DIR}" ]; then
    rm -r "${TEMPLATES_DIR}"
  fi

  if [ "${CHART_NAME}" = "hivemq-swarm" ]; then
      echo "Remove Helm annotations"
      # the BSD implementation of sed does NOT support case-insensitive matching
      sed -i.bak '/helm/d' "${MANIFEST_DIR}"/*.yaml
      sed -i.bak '/Helm/d' "${MANIFEST_DIR}"/*.yaml
  else
    echo "Remove Helm annotations"
    # the BSD implementation of sed does NOT support case-insensitive matching
    sed -i.bak '/helm/d' "${MANIFEST_DIR}"/*.yml
    sed -i.bak '/Helm/d' "${MANIFEST_DIR}"/*.yml
    if [ "${CHART_NAME}" = "hivemq-platform" ]; then
      sed -i.bak '/namespace:/d' "${MANIFEST_DIR}"/*.yml
    fi
  fi
  find . -type f -name "*.bak" -delete
}
