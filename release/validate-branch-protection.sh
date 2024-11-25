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
MANUAL_CHECKS=("continuous-integration/jenkins/branch" "smoke-test-legacy" "smoke-test-platform" "verify")

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
  # parse each job's name and its specific test-plan matrix
  declare -A JOB_TEST_PLANS=()
  while IFS= read -r job_name; do
    test_plans=$(yq eval ".jobs[\"$job_name\"].strategy.matrix.test-plan[]" "$WORKFLOW_FILE" 2>/dev/null)
    JOB_TEST_PLANS["$job_name"]="$test_plans"
  done < <(yq eval '.jobs | keys | .[]' "$WORKFLOW_FILE" 2>/dev/null)

  # generate expected check names based on each job and its test plans
  for job_name in "${!JOB_TEST_PLANS[@]}"; do
    while IFS= read -r test_plan; do
      echo "Found test: $job_name ($test_plan)"
      EXPECTED_CHECKS+=("$job_name ($test_plan)")
    done <<< "${JOB_TEST_PLANS[$job_name]}"
  done
done
echo

# convert array to newline-separated string and sort it for comparison
EXPECTED_CHECKS_STRING=$(printf "%s\n" "${EXPECTED_CHECKS[@]}" | sort)

# get required checks
REQUIRED_CHECKS=$(gh api -H "Accept: application/vnd.github.v3+json" "/repos/$OWNER/$REPO/branches/$BRANCH/protection" | jq -r '.required_status_checks.contexts | @csv' | tr ',' '\n' | tr -d '"' | sort)

# compare expected checks with branch required checks
DIFF=$(comm -23 <(echo "$EXPECTED_CHECKS_STRING") <(echo "$REQUIRED_CHECKS"))
if [ -n "$DIFF" ]; then
  echo "Missing checks in $BRANCH branch protection:"
  echo "$DIFF"
  exit 1
fi
echo "No checks are missing in $BRANCH branch protection"
