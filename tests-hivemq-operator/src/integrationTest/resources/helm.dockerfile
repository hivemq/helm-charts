ARG K3S_TAG=v1.33.2-k3s1@sha256:d8f05b9043d136c3fb01d6cf677caaef304568b8c99bdd359b86d3d7286de1df

FROM ubuntu:noble-20250714@sha256:a08e551cb33850e4740772b38217fc1796a66da2506d312abe51acda354ff061 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
