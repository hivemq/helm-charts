#This is an example that shows how to create an image with non-root user and group permissions.
#Note: The image is not part of the official release of HiveMQ

ARG HIVEMQ_IMAGE=hivemq/hivemq4:k8s-4.8.3
ARG JAVA_IMAGE=openjdk:11-jre-slim

FROM ${HIVEMQ_IMAGE} AS hivemq_image

FROM ${JAVA_IMAGE}

ARG HIVEMQ_IMAGE

ENV HIVEMQ_GID=10000 \
    HIVEMQ_UID=10000

ENV HIVEMQ_LOG_LEVEL INFO

WORKDIR /opt/hivemq

RUN groupadd --gid ${HIVEMQ_GID} hivemq \
    && useradd -g hivemq -d /opt/hivemq -s /bin/bash --uid ${HIVEMQ_UID} hivemq

COPY --from=hivemq_image --chown=hivemq:hivemq /opt/hivemq /opt/hivemq
COPY --from=hivemq_image --chown=hivemq:hivemq /opt/*.sh /opt/
COPY --from=hivemq_image --chown=hivemq:hivemq /docker-entrypoint.d /docker-entrypoint.d/

RUN apt-get update && apt-get install -y --no-install-recommends curl gnupg-agent gnupg unzip libnss-wrapper \
  && apt-get purge -y gpg && apt-get clean && rm -rf /var/lib/apt/lists/*

# Setup permisions to the same group to the configuration and extensions, we are explicit to don't oversize the image
RUN chmod g+w /opt/hivemq/extensions /opt/hivemq/conf /opt/hivemq/extensions/hivemq-prometheus-extension \
     /opt/hivemq/extensions/hivemq-bridge-extension \
     /opt/hivemq/extensions/hivemq-dns-cluster-discovery \
     /opt/hivemq/extensions/hivemq-enterprise-security-extension \
     /opt/hivemq/extensions/hivemq-k8s-sync-extension \
     /opt/hivemq/extensions/hivemq-kafka-extension \
     /opt/hivemq/extensions/hivemq-kafka-extension/DISABLED \
     /opt/hivemq/extensions/hivemq-bridge-extension/DISABLED \
     /opt/hivemq/extensions/hivemq-enterprise-security-extension/DISABLED

# User and group ownership
RUN chown hivemq:hivemq /opt/docker-entrypoint.sh /opt/hivemq \
    && chmod +rx /opt/hivemq/bin/*.sh \
    && chmod 775 /opt/hivemq/ \
    && chmod +rx /opt/pre-entry.sh /opt/hivemq/bin/pre-entry_1.sh /opt/docker-entrypoint.sh

# Set locale
ENV LANG=en_US.UTF-8
# Additional JVM options, may be overwritten by user
ENV JAVA_OPTS "-XX:+UnlockExperimentalVMOptions -XX:+UseNUMA"

# Default allow all extension, set this to false to disable it
ENV HIVEMQ_ALLOW_ALL_CLIENTS "true"

# Enable REST API default value
ENV HIVEMQ_REST_API_ENABLED "false"

# Whether we should print additional debug info for the entrypoints
ENV HIVEMQ_VERBOSE_ENTRYPOINT "true"

# Whether nss_wrapper should be used for starting HiveMQ. Can be disabled for container runtimes that natively fixes the user information in the container at run-time like CRI-O.
ENV HIVEMQ_USE_NSS_WRAPPER "true"

# Use default DNS resolution timeout as default discovery interval
ENV HIVEMQ_DNS_DISCOVERY_TIMEOUT=30

# The default cluster transport bind port to use (UDP port)
ENV HIVEMQ_CLUSTER_PORT=8000 \
    HIVEMQ_CLUSTER_TRANSPORT_TYPE=TCP

ENV HIVEMQ_CONTROL_CENTER_USER=admin \
 HIVEMQ_CONTROL_CENTER_PASSWORD=a68fc32fc49fc4d04c63724a1f6d0c90442209c46dba6975774cde5e5149caf8

VOLUME /opt/hivemq/data

# Persist log data
VOLUME /opt/hivemq/log

# MQTT TCP listener: 1883
# MQTT Websocket listener: 8000
# HiveMQ Control Center: 8080
EXPOSE 1883 8000 8080

WORKDIR /opt/hivemq

USER hivemq

ENTRYPOINT ["/opt/hivemq/bin/pre-entry_1.sh"]
CMD ["/opt/hivemq/bin/run.sh"]
