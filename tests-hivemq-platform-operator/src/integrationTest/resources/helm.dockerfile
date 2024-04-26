ARG K3S_VERSION=v1.29.4-k3s1@sha256:70ffbbd322160e2ae0d830f185d1d3f50741c60a4c8d4b3034af1b1e037ffad5

FROM ubuntu:noble-20240423@sha256:562456a05a0dbd62a671c1854868862a4687bf979a96d48ae8e766642cd911e8 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
