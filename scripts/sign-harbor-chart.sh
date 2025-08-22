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
    local required_binaries=("cosign" "jq" "curl")
    local missing_binaries=()

    for binary in "${required_binaries[@]}"; do
        if ! command -v "$binary" >/dev/null 2>&1; then
            missing_binaries+=("$binary")
        fi
    done

    if [[ ${#missing_binaries[@]} -gt 0 ]]; then
        log_error "Missing required binaries:"
        printf '  - %s\n' "${missing_binaries[@]}" >&2
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

# Harbor API functions
get_harbor_artifact() {
    local chart="$1"
    local version="$2"

    local encoded_creds
    if ! encoded_creds=$(echo -n "${HARBOR_API_USER}:${HARBOR_API_TOKEN}" | base64 | tr -d '\n'); then
        log_error "Failed to base64 encode Harbor API credentials"
        exit 1
    fi
    local auth_header="Basic ${encoded_creds}"

    # URL-encode the repository path
    local encoded_repository=$(echo -n "${chart}" | jq -sRr @uri)
    local url="https://${HARBOR_REGISTRY_URL}/api/v2.0/projects/${HARBOR_PROJECT}/repositories/${encoded_repository}/artifacts/${version}"

    local response
    if ! response=$(curl -sSL \
        --fail-with-body \
        --max-time 30 \
        --header 'Accept: application/json' \
        --header "Authorization: ${auth_header}" \
        "${url}"); then
        log_error "Failed to fetch artifact information from Harbor API"
        log_error "URL: ${url}"
        exit 1
    fi
    
    if ! echo "$response" | jq empty 2>/dev/null; then
        log_error "Invalid JSON response from Harbor API"
        log_error "Response: $response"
        exit 1
    fi
    
    echo "$response"
}

# extract digest from chart
extract_digest() {
    local artifact="$1"
    
    local digest
    digest=$(echo "$artifact" | jq -r '.digest // empty')
    
    if [[ -z "$digest" || "$digest" == "null" ]]; then
        log_error "Could not extract digest from artifact"
        exit 1
    fi
    
    if [[ ! "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]; then
        log_error "Invalid digest format: $digest"
        exit 1
    fi
    
    echo "$digest"
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
    
    # get artifact information
    local artifact
    artifact=$(get_harbor_artifact "$chart_name" "$chart_version")
    
    # extract digest
    local digest
    digest=$(extract_digest "$artifact")
    log_info "Chart digest: $digest"
    
    # sign the chart
    local chart_ref="${HARBOR_REGISTRY_URL}/${HARBOR_PROJECT}/${chart_name}@${digest}"
    sign_chart "$chart_ref"
}

# error handling
trap 'log_error "Script failed on line $LINENO"' ERR

main "$@"
