ARG K3S_VERSION=v1.31.1-k3s1@sha256:880e468a233598a1e7a05722b5859d7ccd93cad17471cdd5d3a2d7ff25e7b91d

FROM ubuntu:noble-20240801@sha256:8a37d68f4f73ebf3d4efafbcf66379bf3728902a8038616808f04e34a9ab63ee AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
