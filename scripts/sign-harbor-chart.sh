#!/usr/bin/env bash
set -e

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
cd "${SCRIPT_DIR}" || exit 1
# shellcheck disable=SC2155
readonly SCRIPT_NAME="$(basename "$0")"

HARBOR_PROJECT=${HARBOR_PROJECT:-"hivemq"}

# required environment variables
readonly REQUIRED_ENV_VARS=(
    "HARBOR_REGISTRY_URL"
    "HARBOR_API_USER"
    "HARBOR_API_TOKEN"
    "COSIGN_PRIVATE_KEY"
    "COSIGN_PASSWORD"
)

# logging functions
log_info() {
    echo "[INFO] $*" >&2
}

log_error() {
    echo "[ERROR] $*" >&2
}

# validate required binaries
validate_binaries() {
    if ! command -v cosign >/dev/null 2>&1; then
        log_error "Missing required binary: cosign"
        exit 1
    fi
}

# validate required environment variables
validate_environment() {
    local missing_vars=()

    for var in "${REQUIRED_ENV_VARS[@]}"; do
        if [[ -z "${!var:-}" ]]; then
            missing_vars+=("$var")
        fi
    done

    if [[ ${#missing_vars[@]} -gt 0 ]]; then
        log_error "Missing required environment variables:"
        printf '  - %s\n' "${missing_vars[@]}" >&2
        exit 1
    fi
}

sign_chart() {
    local chart_ref="$1"
    
    # sign the chart
    if cosign sign -r -y \
        --registry-username="${HARBOR_API_USER}" \
        --registry-password="${HARBOR_API_TOKEN}" \
        --key "env://COSIGN_PRIVATE_KEY" \
        "$chart_ref"; then
        log_info "Successfully signed chart: $chart_ref"
    else
        log_error "Failed to sign chart: $chart_ref"
        exit 1
    fi
}

main() {
    local chart_name="$1"
    local chart_version="$2"

    if [[ -z "$chart_name" || -z "$chart_version" ]]; then
        log_error "Usage: $SCRIPT_NAME <CHART_NAME> <CHART_VERSION>"
        exit 1
    fi

    validate_binaries
    validate_environment

    log_info "Chart components:"
    log_info "  Project: ${HARBOR_PROJECT}"
    log_info "  Chart: $chart_name"
    log_info "  Version: $chart_version"

    local chart_ref="${HARBOR_REGISTRY_URL}/${HARBOR_PROJECT}/${chart_name}:${chart_version}"
    sign_chart "$chart_ref"
}

# error handling
trap 'log_error "Script failed on line $LINENO"' ERR

main "$@"
