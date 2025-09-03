ARG K3S_TAG=v1.33.4-k3s1@sha256:31a85202039b95a75781d537b35e5756ded4b43949f8e1f9f3db8e69d3b73497

FROM ubuntu:noble-20250805@sha256:9cbed754112939e914291337b5e554b07ad7c392491dba6daf25eef1332a22e8 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
