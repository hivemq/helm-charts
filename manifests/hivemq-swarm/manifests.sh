#!/bin/sh

# change current directory to project root
cd "$(dirname "$0")"/../.. || exit 1

echo "Create manifests directory"
mkdir -p manifests && cd manifests || exit 1

echo "Update HiveMQ Swarm dependencies"
helm dependency build ../charts/hivemq-swarm

echo "Create HiveMQ Swarm Templates"
helm template my-swarm ../charts/hivemq-swarm --skip-tests --output-dir . > /dev/null

echo "Flatten directory structure"
find hivemq-swarm/templates -type f -exec mv {} hivemq-swarm/ \; > /dev/null
if [ -d hivemq-swarm/templates ]; then
  rm -r hivemq-swarm/templates
fi

echo "Remove Helm annotations"
# The BSD implementation of sed does NOT support case-insensitive matching
sed -i.bak '/helm/d' hivemq-swarm/*.yaml
sed -i.bak '/Helm/d' hivemq-swarm/*.yaml
find . -type f -name "*.bak" -delete
