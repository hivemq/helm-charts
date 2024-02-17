ARG K3S_VERSION=v1.29.1-k3s1@sha256:ce16e7fd2e884b72987e1a3cb29b9e9f4ff37e9086831e83b3a65fa4e7f1ee17

FROM ubuntu:noble-20240212@sha256:ff0b5139e774bb0dee9ca8b572b4d69eaec2795deb8dc47c8c829becd67de41e AS builder
RUN apt-get update && apt-get install curl gnupg2 apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
