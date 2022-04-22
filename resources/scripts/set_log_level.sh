#!/usr/bin/env bash

# Set the root logger's level to the current dynamic state log level

LOG_LEVEL=$(cat /etc/podinfo/log-level)

if [[ ! -z $1 ]]; then
    echo "Using user-provided log level $1"
    LOG_LEVEL=$1
fi

sed -E -i -e "s|(.*)root level=\".*\"(.*)|\1root\ level=\"${LOG_LEVEL}\"\2|" /opt/hivemq/conf/logback.xml
