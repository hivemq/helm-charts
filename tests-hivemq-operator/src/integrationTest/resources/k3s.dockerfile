ARG K3S_VERSION=v1.29.4-k3s1@sha256:70ffbbd322160e2ae0d830f185d1d3f50741c60a4c8d4b3034af1b1e037ffad5

FROM ubuntu:noble-20240429@sha256:3f85b7caad41a95462cf5b787d8a04604c8262cdcdf9a472b8c52ef83375fe15 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
