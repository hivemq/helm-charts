#!/usr/bin/env bash

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
cd "${SCRIPT_DIR}/../.." || exit 1

# for supported versions see: minikube config defaults kubernetes-version
host=$(minikube status -o json | jq -r '.Host')

if [ "$host" != "Running" ]; then
  minikube start --kubernetes-version v1.28.3
fi
minikube addons enable metrics-server
minikube addons enable dashboard

./gradlew :hivemq-platform-operator:docker --rerun-tasks
minikube image load --overwrite=true hivemq/hivemq-platform-operator-test:snapshot
minikube image load --overwrite=true hivemq/hivemq-platform-operator-init-test:snapshot
