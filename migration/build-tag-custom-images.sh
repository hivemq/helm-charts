#!/usr/bin/env bash

## Building local images for AMD64 architecture based, as EKS cluster instance fails with ARM64 ones.
./gradlew docker --rerun-tasks
docker rmi -f hivemq/hivemq-platform-operator:0.9.0-snapshot hivemq/hivemq-platform-operator-init:0.9.0-snapshot
cd hivemq-platform-operator-init
docker buildx build -f ./build/docker/Dockerfile --platform linux/amd64 ./build/docker --load -t hivemq/hivemq-platform-operator-init:0.9.0-snapshot
docker buildx build -f ./src/main/docker/Dockerfile.jvm --platform linux/amd64 . --load -t hivemq/hivemq-platform-operator:0.9.0-snapshot

aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin 474125479812.dkr.ecr.eu-west-1.amazonaws.com
docker tag hivemq/hivemq-platform-operator-init:0.9.0-snapshot 474125479812.dkr.ecr.eu-west-1.amazonaws.com/hivemq-platform-operator-init:0.9.0-snapshot
docker image push 474125479812.dkr.ecr.eu-west-1.amazonaws.com/hivemq-platform-operator-init:0.9.0-snapshot

docker tag hivemq/hivemq-platform-operator:0.9.0-snapshot 474125479812.dkr.ecr.eu-west-1.amazonaws.com/hivemq-platform-operator:0.9.0-snapshot
docker image push 474125479812.dkr.ecr.eu-west-1.amazonaws.com/hivemq-platform-operator:0.9.0-snapshot

##----------------------------------------------

## Building local images for minikube
minikube image load --overwrite=true hivemq/init-dns-wait:snapshot
minikube image load --overwrite=true hivemq/hivemq4-test:k8s-snapshot

minikube image load --overwrite=true hivemq/hivemq-platform-container:0.9.0-snapshot
minikube image load --overwrite=true hivemq/hivemq-platform-operator-init:0.9.0-snapshot
minikube image load --overwrite=true hivemq/hivemq-platform-operator:0.9.0-snapshot

docker tag hivemq/hivemq-platform-operator-init:0.9.0-snapshot ghcr.io/hivemq/hivemq-platform-operator/hivemq-platform-operator-init:0.9.0-snapshot
minikube image load --overwrite=true ghcr.io/hivemq/hivemq-platform-operator/hivemq-platform-operator-init:0.9.0-snapshot

docker tag hivemq/hivemq-platform-operator:0.9.0-snapshot ghcr.io/hivemq/hivemq-platform-operator/hivemq-platform-operator:0.9.0-snapshot
minikube image load --overwrite=true ghcr.io/hivemq/hivemq-platform-operator/hivemq-platform-operator:0.9.0-snapshot
