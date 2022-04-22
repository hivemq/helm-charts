#!/usr/bin/env bash



# This will also be part of a proper API at some point

set -eo pipefail

set -o xtrace

if [[ $# != 3 ]]; then
    echo "Usage: $0 <extensionUri> <extension-name> <desired-state>"
    exit 1
fi

EXTENSION_URI="$1"
EXTENSION_NAME="$2"
TARGET_STATE="$3"
TARGET_DIR="/opt/hivemq/extensions/${EXTENSION_NAME}"
install_dir=$(mktemp -d)

echo Install Extension ${EXTENSION_NAME}

set +e
[[ -f ${TARGET_DIR}/DISABLED ]]
was_enabled=$?

set -e

if [[ "${EXTENSION_URI}" != "preinstalled" ]]; then
    cd "${install_dir}"
    curl -L "${EXTENSION_URI}" --output extension.zip
    unzip extension.zip
    if [[ -d "${TARGET_DIR}" && -f "${TARGET_DIR}/hivemq-extension.xml" ]]; then
        echo "Extension already installed"
        if [[ "${was_enabled}" == "1" ]]; then
            echo "Extension is currently running, making sure it's disabled first"
            touch ${TARGET_DIR}/DISABLED
            EXT_NAME=$(cat ${TARGET_DIR}/hivemq-extension.xml | grep "<name>" | sed -E "s|.*<name>(.*)</name>|\1|")
            EXT_VERSION=$(cat ${TARGET_DIR}/hivemq-extension.xml | grep "<version>" | sed -E "s|.*<version>(.*)</version>|\1|")
            LINE_COUNT=40 /opt/hivemq/bin/wait_log.sh "Extension \"${EXT_NAME}\" version ${EXT_VERSION} stopped successfully."
        fi
        echo "Extension stopped, deleting old version"
        # Delete old version(s)
        rm -f ${TARGET_DIR}/*.jar
        # Ready to update, proceed as normal
    fi
    echo "Installing new extension version"
    mkdir -p ${TARGET_DIR}
fi

# Disable if target state is false
if [[ "${TARGET_STATE}" == "false" ]]; then
    echo Install Extension ${EXTENSION_NAME} 2
    touch ${TARGET_DIR}/DISABLED
    was_enabled=0
else
# Ensure it is enabled if target state is true
    echo Install Extension ${EXTENSION_NAME} 3
    if [[ -f ${TARGET_DIR}/DISABLED ]]; then
        rm -f ${TARGET_DIR}/DISABLED
        was_enabled=0
    fi
fi



if [[ ${EXTENSION_URI} != "preinstalled" ]]; then
    cd ${install_dir}
    # Move extension into target directory
    cp -r */** ${TARGET_DIR}/
    cd ${TARGET_DIR}
    echo -- Run initialization ${EXTENSION_NAME}
    /opt/hivemq/bin/run_initialization.sh ${EXTENSION_NAME}
    # Make sure the extension will enable, only if the state isn't DISABLED.
    if [[ "${ASYNC_INSTALL}" != "true" ]]; then
        if [[ "${TARGET_STATE}" == "true" ]]; then
            rm -f ${TARGET_DIR}/DISABLED
            TARGET_NAME=$(cat ${TARGET_DIR}/hivemq-extension.xml | grep "<name>" | sed -E "s|.*<name>(.*)</name>|\1|")
            TARGET_VERSION=$(cat ${TARGET_DIR}/hivemq-extension.xml | grep "<version>" | sed -E "s|.*<version>(.*)</version>|\1|")
            LINE_COUNT=20 /opt/hivemq/bin/wait_log.sh "Extension \"${TARGET_NAME}\" version ${TARGET_VERSION} started successfully."
        fi
    fi
    # Cleanup
    cd /
    rm -rf ${install_dir}
else
    echo Executing extension initialization ${EXTENSION_NAME}
    /opt/hivemq/bin/run_initialization.sh ${EXTENSION_NAME}
fi

echo Done install extension ${EXTENSION_NAME}
