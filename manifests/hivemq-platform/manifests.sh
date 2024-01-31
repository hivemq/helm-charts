#!/bin/sh

# change current directory to project root
cd "$(dirname "$0")"/../.. || exit 1

echo "Create manifests directory"
mkdir -p manifests && cd manifests || exit 1

echo "Create HiveMQ Platform Templates"
helm template my-platform ../charts/hivemq-platform --skip-tests --output-dir . > /dev/null

echo "Flatten directory structure"
find hivemq-platform/templates -type f -exec mv {} hivemq-platform/ \; > /dev/null
if [ -d hivemq-platform/templates ]; then
  rm -r hivemq-platform/templates
fi

echo "Remove Helm annotations"
# The BSD implementation of sed does NOT support case-insensitive matching
sed -i.bak '/helm/d' hivemq-platform/*.yml
sed -i.bak '/Helm/d' hivemq-platform/*.yml
sed -i.bak '/namespace:/d' hivemq-platform/*.yml
find . -type f -name "*.bak" -delete
