ARG K3S_VERSION=v1.29.0-k3s1@sha256:adfbb31b694502f11077c47a1360980c915ecb969ac670f8a7ce4b08c0a5ede6

FROM ubuntu:24.04@sha256:50cb325cf61fa0ac0f42c2ea431d8ef091fe3d36f5bc039d15f89c569ff4988e AS builder
RUN apt-get update && apt-get install curl gnupg2 apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
