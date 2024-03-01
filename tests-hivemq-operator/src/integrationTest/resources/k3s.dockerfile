ARG K3S_VERSION=v1.29.2-k3s1@sha256:f6d93da5505360cfec943c777da4a149e2a7b4cbc2ac92c77f38cda5853091ed

FROM ubuntu:noble-20240212@sha256:ff0b5139e774bb0dee9ca8b572b4d69eaec2795deb8dc47c8c829becd67de41e AS builder
RUN apt-get update && apt-get install curl gnupg2 apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
