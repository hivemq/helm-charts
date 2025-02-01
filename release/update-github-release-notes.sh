#!/bin/bash
set -e

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
cd "${SCRIPT_DIR}" || exit 1

# configuration
REPO="hivemq/helm-charts"
RELEASE_COUNT=${1:-200}

# check if binaries are installed
IS_GH_INSTALLED=$(which gh >/dev/null 2>&1 || echo "GitHub CLI is not installed")
if [ -n "$IS_GH_INSTALLED" ]; then
  echo "$IS_GH_INSTALLED"
  exit 1
fi

# check if gh is not authenticated
if ! gh auth status &>/dev/null; then
  echo "GitHub CLI is not logged in"
  echo "Please run 'gh auth login' to authenticate"
  exit 1
fi

cd .. || exit 1

curl -L -s -o charts.yaml https://raw.githubusercontent.com/hivemq/helm-charts/refs/heads/gh-pages/index.yaml
gh release list --repo "$REPO" --limit "$RELEASE_COUNT" --json name,tagName,publishedAt > releases.json

GH_PATH=$(which gh)
PWD=$(pwd)
./gradlew :github-release-note-updater:run --args " -g $GH_PATH -p $PWD"

rm charts.yaml releases.json
