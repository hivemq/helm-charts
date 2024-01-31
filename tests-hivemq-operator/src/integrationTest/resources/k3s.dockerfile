ARG K3S_VERSION=v1.29.1-k3s1@sha256:ce16e7fd2e884b72987e1a3cb29b9e9f4ff37e9086831e83b3a65fa4e7f1ee17

FROM ubuntu:noble-20240114@sha256:50cb325cf61fa0ac0f42c2ea431d8ef091fe3d36f5bc039d15f89c569ff4988e AS builder
RUN apt-get update && apt-get install curl gnupg2 apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
