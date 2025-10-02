ARG K3S_TAG=v1.34.1-k3s1@sha256:5e0707cfd1239b358ef73f3254bc3eadc027dd30cd5ec6ca41e29e47652a1b8c

FROM ubuntu:noble-20250925@sha256:b5e73ebf4acb88f11ad149d295f3acf22e5d910a425a9a57faeefed165266a17 AS builder
RUN apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
