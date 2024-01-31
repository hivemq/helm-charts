ARG K3S_VERSION=v1.21.14-k3s1@sha256:85745e4fa94050ead9c8a935c2a2136bfdfe107c3592fb229fb6aff26640ca72

FROM ubuntu:noble-20240114@sha256:50cb325cf61fa0ac0f42c2ea431d8ef091fe3d36f5bc039d15f89c569ff4988e AS builder
RUN apt-get update && apt-get install curl gnupg2 apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
