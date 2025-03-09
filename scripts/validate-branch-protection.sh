#!/usr/bin/env bash
set -e

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
cd "${SCRIPT_DIR}" || exit 1

# configuration
OWNER="hivemq"
REPO="helm-charts"
BRANCH="${1:-develop}"
WORKFLOW_FILES=(
  "../.github/workflows/hivemq-operator-integration-test.yml"
  "../.github/workflows/hivemq-platform-operator-integration-test.yml"
)
MANUAL_CHECKS=("continuous-integration/jenkins/branch" "smoke-test-legacy" "smoke-test-platform" "smoke-test-edge" "verify" "Generate split indexes" "Merge JUnit reports")

# check bash version
if ((BASH_VERSINFO < 4)); then
  echo "Bash >= 4.x must be installed"
  exit 1
fi

# check if binaries are installed
IS_GH_INSTALLED=$(which gh >/dev/null 2>&1 || echo "GitHub CLI is not installed")
if [ -n "$IS_GH_INSTALLED" ]; then
  echo "$IS_GH_INSTALLED"
  exit 1
fi
IS_JQ_INSTALLED=$(which jq >/dev/null 2>&1 || echo "jq is not installed")
if [ -n "$IS_JQ_INSTALLED" ]; then
  echo "$IS_JQ_INSTALLED"
  exit 1
fi
IS_YQ_INSTALLED=$(which yq >/dev/null 2>&1 || echo "yq is not installed")
if [ -n "$IS_YQ_INSTALLED" ]; then
  echo "$IS_YQ_INSTALLED"
  exit 1
fi
IS_COMM_INSTALLED=$(which comm >/dev/null 2>&1 || echo "comm is not installed")
if [ -n "$IS_COMM_INSTALLED" ]; then
  echo "$IS_COMM_INSTALLED"
  exit 1
fi

# check if gh is not authenticated
if ! gh auth status &>/dev/null; then
  echo "GitHub CLI is not logged in"
  echo "Please run 'gh auth login' to authenticate"
  exit 1
fi

# process each specified workflow file
EXPECTED_CHECKS=("${MANUAL_CHECKS[@]}")
for WORKFLOW_FILE in "${WORKFLOW_FILES[@]}"; do
  echo "Analyzing $WORKFLOW_FILE..."

  CHECK_PREFIX=$(yq eval ".env.check-prefix" "$WORKFLOW_FILE" 2>/dev/null)
  SPLIT_TOTAL=$(yq eval ".env.split-total" "$WORKFLOW_FILE" 2>/dev/null)
  echo "Found $SPLIT_TOTAL test splits"

  # parse each job's name and its specific test-plan matrix
  declare -A JOB_K8S_VERSION_TYPES=()
  while IFS= read -r JOB_NAME; do
    K8S_VERSION_TYPES=$(yq eval ".jobs[\"$JOB_NAME\"].strategy.matrix.k8s-version-type[]" "$WORKFLOW_FILE" 2>/dev/null)
    if [ -n "$K8S_VERSION_TYPES" ]; then
      echo "Found K8s version types for job '$JOB_NAME'"
      JOB_K8S_VERSION_TYPES["$JOB_NAME"]="$K8S_VERSION_TYPES"
    fi
  done < <(yq eval '.jobs | keys | .[]' "$WORKFLOW_FILE" 2>/dev/null)

  # generate expected check names based on each job and its test plans
  for JOB_NAME in "${!JOB_K8S_VERSION_TYPES[@]}"; do
    for ((SPLIT_INDEX=0; SPLIT_INDEX < SPLIT_TOTAL; SPLIT_INDEX++)); do
      while IFS= read -r K8S_VERSION_TYPE; do
        echo "Found test: $CHECK_PREFIX$SPLIT_INDEX ($K8S_VERSION_TYPE)"
        EXPECTED_CHECKS+=("$CHECK_PREFIX$SPLIT_INDEX ($K8S_VERSION_TYPE)")
      done <<< "${JOB_K8S_VERSION_TYPES[$JOB_NAME]}"
    done
  done
done
echo

# convert array to newline-separated string and sort it for comparison
EXPECTED_CHECKS_STRING=$(printf "%s\n" "${EXPECTED_CHECKS[@]}" | sort)

# get required checks
REQUIRED_CHECKS=$(gh api -H "Accept: application/vnd.github.v3+json" "/repos/$OWNER/$REPO/branches/$BRANCH/protection" | jq -r '.required_status_checks.contexts[]' | sort)

# compare expected checks with branch required checks
DIFF=$(comm -23 <(echo "$EXPECTED_CHECKS_STRING") <(echo "$REQUIRED_CHECKS"))
if [ -n "$DIFF" ]; then
  echo "Missing checks in $BRANCH branch protection:"
  echo "$DIFF"
  exit 1
fi
echo "No checks are missing in $BRANCH branch protection"
