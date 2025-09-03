ARG K3S_TAG=v1.33.4-k3s1@sha256:31a85202039b95a75781d537b35e5756ded4b43949f8e1f9f3db8e69d3b73497

FROM ubuntu:noble-20250805@sha256:fbd21645c5e6b7cef7e72c83119f53be63d3296f84dbdc5717a0af3446b852fe AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
