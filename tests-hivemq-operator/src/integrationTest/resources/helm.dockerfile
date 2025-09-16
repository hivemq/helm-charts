ARG K3S_TAG=v1.33.4-k3s1@sha256:31a85202039b95a75781d537b35e5756ded4b43949f8e1f9f3db8e69d3b73497

FROM ubuntu:noble-20250910@sha256:a43abc7a9e6ad7d1831d20f1aaa2d0c96a8069de441b5b5c9616eb88b7cd12b1 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
