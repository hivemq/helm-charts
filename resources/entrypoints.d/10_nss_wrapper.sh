#!/usr/bin/env bash

if ! getent passwd "$(id -u)" &> /dev/null && [ -e /usr/lib/libnss_wrapper.so ] && [[ ${HIVEMQ_USE_NSS_WRAPPER} = "true" ]]; then
    USER_ID=$(id -u)
    GROUP_ID=$(id -g)
    HOME=/opt/hivemq

    grep -v -e ^hivemq -e "^$USER_ID" /etc/passwd > "$HOME/passwd"
    echo "hivemq:x:${USER_ID}:${GROUP_ID}:HiveMQ:${HOME}:/bin/false" >> "$HOME/passwd"

    export LD_PRELOAD=/usr/lib/libnss_wrapper.so
    export NSS_WRAPPER_PASSWD=${HOME}/passwd
    export NSS_WRAPPER_GROUP=/etc/group
fi