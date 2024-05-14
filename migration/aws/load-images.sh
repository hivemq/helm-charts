#!/usr/bin/env bash

AWS_ECR_REPO=474125479812.dkr.ecr.eu-west-1.amazonaws.com
AWS_REGION=eu-west-1
HIVEMQ_PLATFORM_OPERATOR_IMAGE=hivemq-platform-operator-test:snapshot
HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE=hivemq-platform-operator-init-test:snapshot
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
cd "${SCRIPT_DIR}/../../../hivemq-platform-operator" || exit 1

## Building local images for AMD64 architecture based, as EKS cluster instance fails with ARM64 ones.
./gradlew :docker --rerun-tasks
docker buildx build -f ./hivemq-platform-operator-init/build/docker/Dockerfile --platform linux/amd64 ./hivemq-platform-operator-init/build/docker --load -t ${AWS_ECR_REPO}/${HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE}
docker buildx build -f ./src/main/docker/Dockerfile.jvm --platform linux/amd64 . --load -t ${AWS_ECR_REPO}/${HIVEMQ_PLATFORM_OPERATOR_IMAGE}

aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ECR_REPO}
docker image push ${AWS_ECR_REPO}/${HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE}
docker image push ${AWS_ECR_REPO}/${HIVEMQ_PLATFORM_OPERATOR_IMAGE}
