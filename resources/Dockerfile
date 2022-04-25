#Additional build image to unpack the zip file and change the permissions without retaining large layers just for those operations
ARG HIVEMQ_VERSION=4.7.6

FROM busybox as unpack
ARG HIVEMQ_VERSION

ARG DNS_DISCOVERY_EXTENSION_VERSION=4.1.0

# get all the files into place
WORKDIR /download

RUN wget https://www.hivemq.com/releases/hivemq-${HIVEMQ_VERSION}.zip -O hivemq.zip \
    && wget https://www.hivemq.com/releases/extensions/hivemq-k8s-sync-extension-4.7.5.zip -O hivemq-k8s-sync-extension.zip \
    && wget https://github.com/hivemq/hivemq-dns-cluster-discovery-extension/releases/download/${DNS_DISCOVERY_EXTENSION_VERSION}/hivemq-dns-cluster-discovery-${DNS_DISCOVERY_EXTENSION_VERSION}.zip -O  hivemq-dns-cluster-discovery.zip \
    && wget https://www.hivemq.com/releases/extensions/hivemq-prometheus-extension-4.0.4.zip -O hivemq-prometheus-extension.zip

RUN  unzip hivemq.zip  -d /opt/ \
     && rm -rf /opt/hivemq-${HIVEMQ_VERSION}/tools/hivemq-swarm \
     && ln -s /opt/hivemq-${HIVEMQ_VERSION} /opt/hivemq

RUN unzip hivemq-k8s-sync-extension.zip -d /opt/hivemq/extensions/hivemq-k8s-sync-extension \
    && unzip hivemq-prometheus-extension.zip -d /opt/hivemq/extensions/ \
    && unzip hivemq-dns-cluster-discovery.zip -d /opt/hivemq/extensions \
    && rm -f /opt/hivemq/extensions/hivemq-dns-cluster-discovery/*.png

COPY config.xml /opt/hivemq-${HIVEMQ_VERSION}/conf/config.xml
COPY docker-entrypoint.sh /opt/docker-entrypoint.sh
COPY entrypoints.d/* /docker-entrypoint.d/
COPY config-dns.xml /opt/hivemq/conf/config.xml
COPY pre-entry.sh /opt/pre-entry.sh
COPY scripts/*.sh /opt/hivemq/bin/
COPY config.xml /opt/hivemq/conf/config.xml
COPY prometheusConfiguration.properties /opt/hivemq/extensions/hivemq-prometheus-extension/

#target image
FROM openjdk:11-jre-slim
ARG HIVEMQ_VERSION
ENV HIVEMQ_GID=10000
ENV HIVEMQ_UID=10000

ENV HIVEMQ_LOG_LEVEL INFO

WORKDIR /opt/hivemq
RUN groupadd --gid ${HIVEMQ_GID} hivemq \
    && useradd -g hivemq -d /opt/hivemq -s /bin/bash --uid ${HIVEMQ_UID} hivemq

COPY --from=unpack --chown=hivemq:hivemq /opt /opt
COPY --from=unpack --chown=hivemq:hivemq /docker-entrypoint.d /docker-entrypoint.d/

RUN apt-get update && apt-get install -y --no-install-recommends curl gnupg-agent gnupg dirmngr unzip libnss-wrapper\
    && if [ -d /docker-entrypoint.d ]; then \
      mv /opt/hivemq/bin/pre-entry_1.sh /docker-entrypoint.d/15_k8s_entrypoint.sh && \
      ln -s /opt/docker-entrypoint.sh /opt/hivemq/bin/pre-entry_1.sh; \
    else \
      echo '# Pass on to DNS entrypoint' >> /opt/hivemq/bin/pre-entry_1.sh && \
      echo 'exec /opt/pre-entry.sh "$@"' >> /opt/hivemq/bin/pre-entry_1.sh; \
    fi \
    && ln -s /opt/hivemq-${HIVEMQ_VERSION} /opt/hivemq \
    && ln -s /opt/pre-entry.sh /docker-entrypoint.d/40_dns_entrypoint.sh \
    && chmod g+w /opt/hivemq-${HIVEMQ_VERSION} /opt/hivemq/extensions /opt/hivemq/conf /opt/hivemq/extensions/hivemq-prometheus-extension /opt/hivemq/extensions/hivemq-bridge-extension  /opt/hivemq/extensions/hivemq-dns-cluster-discovery  /opt/hivemq/extensions/hivemq-enterprise-security-extension  /opt/hivemq/extensions/hivemq-k8s-sync-extension  /opt/hivemq/extensions/hivemq-kafka-extension /opt/hivemq/extensions/hivemq-kafka-extension/DISABLED /opt/hivemq/extensions/hivemq-bridge-extension/DISABLED /opt/hivemq/extensions/hivemq-enterprise-security-extension/DISABLED  \
    && chown hivemq:hivemq /opt/docker-entrypoint.sh \
    && chmod +rx /opt/hivemq/bin/*.sh \
    && chmod +rx /opt/pre-entry.sh /opt/hivemq/bin/pre-entry_1.sh /opt/docker-entrypoint.sh \
    && apt-get purge -y gpg dirmngr && apt-get clean && rm -rf /var/lib/apt/lists/* 


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

# Set locale
ENV LANG=en_US.UTF-8

# Use default DNS resolution timeout as default discovery interval
ENV HIVEMQ_DNS_DISCOVERY_INTERVAL 31
ENV HIVEMQ_DNS_DISCOVERY_TIMEOUT 30

# The default cluster transport bind port to use (UDP port)
ENV HIVEMQ_CLUSTER_PORT 8000
ENV HIVEMQ_CONTROL_CENTER_USER admin
ENV HIVEMQ_CONTROL_CENTER_PASSWORD a68fc32fc49fc4d04c63724a1f6d0c90442209c46dba6975774cde5e5149caf8
ENV HIVEMQ_CLUSTER_TRANSPORT_TYPE UDP

ENV HIVEMQ_CLUSTER_TRANSPORT_TYPE TCP

# Make broker data persistent throughout stop/start cycles
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