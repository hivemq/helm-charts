ARG K3S_VERSION=v1.30.3-k3s1@sha256:c120ff5860f8cfa9852e495ad6339579364a582cbf9b27d189456a301abfeb44

FROM ubuntu:noble-20240605@sha256:2e863c44b718727c860746568e1d54afd13b2fa71b160f5cd9058fc436217b30 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
