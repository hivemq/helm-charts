#!/usr/bin/env bash

# change current directory to project root
cd "$(dirname "$0")"/../.. || exit 1

echo "Create manifests directory"
mkdir -p manifests && cd manifests || exit 1

echo "Create HiveMQ Operator Templates"
helm template hivemq-operator ../charts/hivemq-operator -n hivemq -f ./hivemq-operator/manifest.yaml --skip-tests --output-dir . > /dev/null

echo "Flatten directory structure"
find hivemq-operator/templates -type f -exec mv {} hivemq-operator/operator/ \; > /dev/null
if [ -d hivemq-operator/templates ]; then
  rm -r hivemq-operator/templates
fi

# Shorten Helm's a little redundant naming
sed -i.bak 's|operator\-operator|operator|' hivemq-operator/operator/*.yaml
find . -type f -name "*.bak" -delete
