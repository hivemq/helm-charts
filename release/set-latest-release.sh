#!/usr/bin/env bash
set -e

# configuration
REPO="hivemq/helm-charts"

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

# get a list of releases with names matching "hivemq-platform-<version>"
echo "Searching the latest hivemq-platform release"
releases=$(gh release list --repo "$REPO" --limit 100 | grep -oP '^hivemq-platform-\K[0-9]+\.[0-9]+\.[0-9]+')
latest_version=$(echo "$releases" | sort -V | tail -n 1)
if [ -z "$latest_version" ]; then
  echo "No hivemq-platform releases found"
  exit 1
fi

# mark the found release as latest
latest_release="hivemq-platform-$latest_version"
gh release edit "$latest_release" --repo "$REPO" --latest
echo "Marked $latest_release as the latest release"
