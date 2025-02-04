ARG K3S_TAG=v1.32.1-k3s1@sha256:2ce69284b7f28bd1bde9f8503f0eb7fe77943cdbe3a88564b73ed6aef8b180d3

FROM ubuntu:noble-20250127@sha256:72297848456d5d37d1262630108ab308d3e9ec7ed1c3286a32fe09856619a782 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
