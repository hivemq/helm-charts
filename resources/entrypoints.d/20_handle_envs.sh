#!/usr/bin/env bash

# Decode license and put file if present
if [[ -n "${HIVEMQ_LICENSE}" ]]; then
    echo >&3 "Decoding license..."
    echo "${HIVEMQ_LICENSE}" | base64 -d > /opt/hivemq/license/license.lic
fi

# We set the bind address here to ensure HiveMQ uses the correct interface. Defaults to using the container hostname (which should be hardcoded in /etc/hosts)
if [[ -z "${HIVEMQ_BIND_ADDRESS}" ]]; then
    echo >&3 "Getting bind address from container hostname"
    HIVEMQ_BIND_ADDRESS=$(getent hosts ${HOSTNAME} | grep -v 127.0.0.1 | awk '{ print $1 }' | head -n 1)
    export HIVEMQ_BIND_ADDRESS
else
    echo >&3 "HiveMQ bind address was overridden by environment variable (value: ${HIVEMQ_BIND_ADDRESS})"
fi

# Remove allow all extension if applicable
if [[ "${HIVEMQ_ALLOW_ALL_CLIENTS}" != "true" ]]; then
    echo "Disabling allow all extension"
    rm -rf /opt/hivemq/extensions/hivemq-allow-all-extension &>/dev/null || true
fi

if [[ "${HIVEMQ_REST_API_ENABLED}" == "true" ]]; then
  REST_API_ENABLED_CONFIGURATION="<rest-api>
        <enabled>true</enabled>
        <listeners>
            <http>
                <port>8888</port>
                <bind-address>0.0.0.0</bind-address>
            </http>
        </listeners>
    </rest-api>"
  echo "Enabling REST API in config.xml..."
  REST_API_ENABLED_CONFIGURATION="${REST_API_ENABLED_CONFIGURATION//$'\n'/}"
  sed -i "s|<\!--REST-API-CONFIGURATION-->|${REST_API_ENABLED_CONFIGURATION}|" /opt/hivemq/conf/config.xml
fi

echo >&3 "setting bind address to ${HIVEMQ_BIND_ADDRESS}"