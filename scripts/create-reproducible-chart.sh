#!/usr/bin/env bash

# A script to take a Helm chart .tgz and make it reproducible.
# Note: This script is only compatible on Linux based OS.
# Usage: ./create-reproducible-chart.sh <input-chart.tgz> <output-chart.tgz>

set -e

INPUT_TGZ=$1
OUTPUT_TGZ=$2

if [[ -z "$INPUT_TGZ" || -z "$OUTPUT_TGZ" ]]; then
  echo "Usage: $0 <INPUT-CHART.TGZ> <OUTPUT-CHART.TGZ>"
  exit 1
fi

# create a secure temporary directory for extraction
TMP_DIR=$(mktemp -d)

# ensure cleanup happens even if the script is interrupted
trap 'rm -rf -- "$TMP_DIR"' EXIT

# extract the original, non-deterministic archive
tar -xzf "$INPUT_TGZ" -C "$TMP_DIR"

chart_dir=$(ls "$TMP_DIR")

# re-create the archive with deterministic options
tar -czf "$OUTPUT_TGZ" \
  --sort=name \
  --mtime='@0' \
  --owner=0 --group=0 \
  --numeric-owner \
  -C "$TMP_DIR" "$chart_dir" 2>/dev/null

# set the modification time to the Unix epoch for full determinism
touch --date=@0 "$OUTPUT_TGZ"

echo "Successfully created reproducible archive: $OUTPUT_TGZ"
