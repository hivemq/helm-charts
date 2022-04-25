#!/usr/bin/env bash

if [[ "${HIVEMQ_CLUSTER_TRANSPORT_TYPE}" == "UDP" ]]; then
    # shellcheck disable=SC2016
    sed -i -e 's|<\!--TRANSPORT_TYPE-->|<udp><bind-address>${HIVEMQ_BIND_ADDRESS}</bind-address><bind-port>${HIVEMQ_CLUSTER_PORT}</bind-port><!-- disable multicast to avoid accidental cluster forming --><multicast-enabled>false</multicast-enabled></udp>|' /opt/hivemq/conf/config.xml
elif [[ "${HIVEMQ_CLUSTER_TRANSPORT_TYPE}" == "TCP" ]]; then
    # shellcheck disable=SC2016
    sed -i -e 's|<\!--TRANSPORT_TYPE-->|<tcp><bind-address>${HIVEMQ_BIND_ADDRESS}</bind-address><bind-port>${HIVEMQ_CLUSTER_PORT}</bind-port></tcp>|' /opt/hivemq/conf/config.xml
fi