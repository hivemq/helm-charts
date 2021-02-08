#!/usr/bin/env bash

helm template hivemq-operator ../charts/hivemq-operator -n hivemq -f manifest.yaml --output-dir .
# Remove test job manifests
rm -r hivemq-operator/templates/tests
# Flatten dir structure
find hivemq-operator -type f -exec mv -i {} hivemq-operator/ \;
rm -r hivemq-operator/templates
# Move to manifest dir
mv hivemq-operator/*.yaml operator/
# Shorten Helm's a little redundant naming
sed -i '' 's|operator\-operator|operator|' operator/*.yaml
# Cleanup
rm -r hivemq-operator