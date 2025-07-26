ARG K3S_TAG=v1.33.3-k3s1@sha256:044ed1528f02aeb9c83cc640c1785fddf19d6fbcfc77976c659979d58716fb09

FROM ubuntu:noble-20250714@sha256:a08e551cb33850e4740772b38217fc1796a66da2506d312abe51acda354ff061 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
