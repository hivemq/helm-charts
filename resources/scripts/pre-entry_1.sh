#!/usr/bin/env bash

echo Set write permissions to extensions

chmod g+w -R /opt/hivemq/extensions

if [[ "${HIVEMQ_VERBOSE_ENTRYPOINT}" == "true" ]]; then
  set -o xtrace
fi

# Kill child scripts (-> extension install) and exit self
trap 'echo "Received SIGTERM during initialization, exiting..." && kill -9 ${PIDS} && exit 1' TERM

PIDS=""

echo "Copying external files"

chmod -R 777 /hivemq-data/*
cp -rsv /hivemq-data/* /opt/hivemq/

cp -rsv /conf-override/* /opt/hivemq/

# For config.xml from yaml
if [[ -n ${HIVEMQ_CONFIG_OVERRIDE} ]]; then
    echo "Rewriting config.xml..."
    echo "${HIVEMQ_CONFIG_OVERRIDE}" > /opt/hivemq/conf/config.xml
    HIVEMQ_LISTENER_CONFIGURATION="${HIVEMQ_LISTENER_CONFIGURATION//$'\n'/}"
    sed -i "s|--LISTENER-CONFIGURATION--|${HIVEMQ_LISTENER_CONFIGURATION}|" /opt/hivemq/conf/config.xml

    HIVEMQ_REST_API_CONFIGURATION="${HIVEMQ_REST_API_CONFIGURATION//$'\n'/}"
    sed -i "s|<\!--REST-API-CONFIGURATION-->|${HIVEMQ_REST_API_CONFIGURATION}|" /opt/hivemq/conf/config.xml
fi

# For each file mounted from a configMap: Create an initial .lastUpdate file so we can reconcile if anything needs to be restarted due to a missed MODIFY notification
echo "Creating initial lastUpdate files..."
for file in $(find /conf-override -type f | grep -v /conf-override/data); do
    cp -rv "${file}" "${file/\/conf-override//opt/hivemq}.lastUpdate"
    chmod 777 "${file/\/conf-override//opt/hivemq}.lastUpdate"
done
for file in $(find /hivemq-data -type f); do
    cp -rv "${file}" "${file/\/hivemq-data//opt/hivemq}.lastUpdate"
    chmod 777 "${file/\/hivemq-data//opt/hivemq}.lastUpdate"
done

if [[ ${HIVEMQ_ENABLE_PROMETHEUS} != "true" ]]; then
  echo "Disabling Prometheus"
  # For standard docker interoperability
  # mkdir -p /opt/hivemq/extensions/hivemq-prometheus-extension/
  touch /opt/hivemq/extensions/hivemq-prometheus-extension/DISABLED
fi

cp -s /etc/podinfo/replica-count /opt/hivemq/initial_node_count

echo "Pod info:"
cat /etc/podinfo/extension-state

# Initialize extension state variables
mapfile -d ' ' -t EXTENSION_NAMES < <(grep extension-names /etc/podinfo/extension-state | sed -E 's|^extension-names=(.*)|\1|' | tr -d '\n')
mapfile -d ' ' -t EXTENSION_STATES < <(grep extension-states /etc/podinfo/extension-state | sed -E 's|^extension-states=(.*)|\1|' | tr -d '\n')
mapfile -d ' ' -t EXTENSION_URIS < <(grep extension-uris /etc/podinfo/extension-state | sed -E 's|^extension-uris=(.*)|\1|' | tr -d '\n')

if [[ ${#EXTENSION_NAMES[@]} -ge 1 ]]; then
    for ((iter=0; iter<${#EXTENSION_NAMES[@]}; iter++)); do
        echo "Installing extension #$iter with name: ${EXTENSION_NAMES[$iter]}, URI: ${EXTENSION_URIS[$iter]}, enabled state: ${EXTENSION_STATES[$iter]}"
        ASYNC_INSTALL=true /opt/hivemq/bin/install_extension.sh "${EXTENSION_URIS[$iter]}" "${EXTENSION_NAMES[$iter]}" "${EXTENSION_STATES[$iter]}" &
        PIDS="$PIDS $!"
        echo "Waiting"
        wait $!
        echo "Done Waiting"
    done
    echo "Done all extensions"
fi

/opt/hivemq/bin/set_log_level.sh

mkdir -p /opt/hivemq/log

# Link to the mapping properties file for auto file syncing
cat /etc/podinfo/config-state
cp -s /etc/podinfo/config-state /opt/hivemq/extensions/hivemq-k8s-sync-extension/mapping.properties
echo Done pre_entry_1_s

if [[ "${HIVEMQ_CLUSTER_TRANSPORT_TYPE}" == "UDP" ]]; then
    # shellcheck disable=SC2016
    sed -i -e 's|<\!--TRANSPORT_TYPE-->|<udp><bind-address>${HIVEMQ_BIND_ADDRESS}</bind-address><bind-port>${HIVEMQ_CLUSTER_PORT}</bind-port><!-- disable multicast to avoid accidental cluster forming --><multicast-enabled>false</multicast-enabled></udp>|' /opt/hivemq/conf/config.xml
elif [[ "${HIVEMQ_CLUSTER_TRANSPORT_TYPE}" == "TCP" ]]; then
    # shellcheck disable=SC2016
    sed -i -e 's|<\!--TRANSPORT_TYPE-->|<tcp><bind-address>${HIVEMQ_BIND_ADDRESS}</bind-address><bind-port>${HIVEMQ_CLUSTER_PORT}</bind-port></tcp>|' /opt/hivemq/conf/config.xml
fi
echo executed pre-entry_s

ls -l /opt/hivemq/bin

/opt/hivemq/bin/run.sh
