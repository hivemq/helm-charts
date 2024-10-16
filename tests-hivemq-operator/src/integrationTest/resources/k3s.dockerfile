ARG K3S_VERSION=v1.31.1-k3s1@sha256:880e468a233598a1e7a05722b5859d7ccd93cad17471cdd5d3a2d7ff25e7b91d

FROM ubuntu:noble-20241011@sha256:ff4bc1c0d3b73a9cc5059118b410ef9dd27bee460484bcc9e645900da79ae7a6 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
