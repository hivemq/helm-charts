#!/bin/sh

# change current directory to project root
cd "$(dirname "$0")"/../.. || exit 1

echo "Create manifests directory"
mkdir -p manifests && cd manifests || exit 1

echo "Create Platform Operator Templates"
helm template my-operator ../charts/hivemq-platform-operator --skip-tests --output-dir . > /dev/null

echo "Flatten directory structure"
find hivemq-platform-operator/templates -type f -exec mv {} hivemq-platform-operator/ \; > /dev/null
if [ -d hivemq-platform-operator/templates ]; then
  rm -r hivemq-platform-operator/templates
fi

echo "Remove Helm annotations"
# The BSD implementation of sed does NOT support case-insensitive matching
sed -i.bak '/helm/d' hivemq-platform-operator/*.yml
sed -i.bak '/Helm/d' hivemq-platform-operator/*.yml
find . -type f -name "*.bak" -delete
