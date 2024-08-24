ARG K3S_VERSION=v1.30.4-k3s1@sha256:983a88efba91ce49efa8af580feaf23cdcd8bf9f3f98ac6b4839162e2aeb1897

FROM ubuntu:noble-20240801@sha256:8a37d68f4f73ebf3d4efafbcf66379bf3728902a8038616808f04e34a9ab63ee AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
