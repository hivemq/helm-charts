ARG K3S_VERSION=v1.29.2-k3s1@sha256:f6d93da5505360cfec943c777da4a149e2a7b4cbc2ac92c77f38cda5853091ed

FROM ubuntu:noble-20240225@sha256:723ad8033f109978f8c7e6421ee684efb624eb5b9251b70c6788fdb2405d050b AS builder
RUN apt-get update && apt-get install curl gnupg2 apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
