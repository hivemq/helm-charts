ARG K3S_VERSION=v1.30.1-k3s1@sha256:09e019280cdc89d038644f1656ac7f2aed98807bd97c20e2dc1b5b9f534a0718

FROM ubuntu:noble-20240429@sha256:3f85b7caad41a95462cf5b787d8a04604c8262cdcdf9a472b8c52ef83375fe15 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
