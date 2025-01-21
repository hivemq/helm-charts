ARG K3S_VERSION=v1.32.0-k3s1@sha256:b7a31cc883051a89334a7865a7e75ef0bfcec5a5d13aba228b37388160a0be75

FROM ubuntu:noble-20241015@sha256:278628f08d4979fb9af9ead44277dbc9c92c2465922310916ad0c46ec9999295 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_VERSION}
COPY --from=builder /usr/local/bin/helm /bin/
