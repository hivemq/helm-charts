ARG K3S_TAG=v1.33.4-k3s1@sha256:31a85202039b95a75781d537b35e5756ded4b43949f8e1f9f3db8e69d3b73497

FROM ubuntu:noble-20250716@sha256:7c06e91f61fa88c08cc74f7e1b7c69ae24910d745357e0dfe1d2c0322aaf20f9 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
