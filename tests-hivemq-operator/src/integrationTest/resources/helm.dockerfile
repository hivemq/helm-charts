ARG K3S_TAG=v1.34.1-k3s1@sha256:5e0707cfd1239b358ef73f3254bc3eadc027dd30cd5ec6ca41e29e47652a1b8c

FROM ubuntu:noble-20251001@sha256:66460d557b25769b102175144d538d88219c077c678a49af4afca6fbfc1b5252 AS builder
RUN set -e; \
    try_mirror() { \
        mirror="$1"; \
        echo "Trying Ubuntu mirror: $mirror"; \
        sed -i -E "s|^(URIs: )http[s]?://[^/]+/[^ ]*|\1${mirror}|g" /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || \
        sed -i -E "s|http[s]?://[^/]+/ubuntu/?|${mirror}|g" /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || true; \
        attempt=1; \
        while [ $attempt -le 3 ]; do \
            echo "apt-get update attempt $attempt of 3 for mirror $mirror..."; \
            if apt-get update -o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 2>&1; then \
                echo "apt-get update succeeded with mirror $mirror"; \
                return 0; \
            else \
                echo "apt-get update failed with mirror $mirror (attempt $attempt)"; \
                if [ $attempt -lt 3 ]; then \
                    sleep 2; \
                fi; \
                apt-get clean; \
                rm -rf /var/lib/apt/lists/*; \
            fi; \
            attempt=$((attempt + 1)); \
        done; \
        return 1; \
    }; \
    try_mirror "http://archive.ubuntu.com/ubuntu/" || \
    try_mirror "http://us.archive.ubuntu.com/ubuntu/" || \
    try_mirror "http://de.archive.ubuntu.com/ubuntu/" || \
    try_mirror "http://mirror.aarnet.edu.au/pub/ubuntu/archive/" || \
    (echo "apt-get update failed with all mirrors" && exit 100); \
    attempt=1; \
    while [ $attempt -le 3 ]; do \
        echo "apt-get install attempt $attempt of 3..."; \
        if apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq -o Acquire::Retries=3 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30; then \
            echo "apt-get install succeeded"; \
            break; \
        else \
            echo "apt-get install failed (attempt $attempt)"; \
            if [ $attempt -lt 3 ]; then \
                sleep $((attempt * 2)); \
            else \
                echo "apt-get install failed after 3 attempts"; \
                exit 100; \
            fi; \
        fi; \
        attempt=$((attempt + 1)); \
    done
RUN curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
    && bash get_helm.sh

FROM rancher/k3s:${K3S_TAG}
COPY --from=builder /usr/local/bin/helm /bin/
