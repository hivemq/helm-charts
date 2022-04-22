#!/usr/bin/env bash

if [[ $# != 1 ]]; then
    echo "Usage: $0 <log-statement>"
    exit 1
fi
RECENT_LINE_COUNT=${LINE_COUNT:-2}
COUNT=0
LOG_STATEMENT=$1


function line_matches_or_error() {
    LAST_LINES=$(tail -n ${RECENT_LINE_COUNT} /opt/hivemq/log/hivemq.log)
    MATCHING=false
    if echo ${LAST_LINES} | grep -q -e "$1"; then
        MATCHING=true
    fi
    if [[ ! -z ${ERROR_STATEMENT} ]]; then
        if cat ${LAST_LINES} | grep -e "${ERROR_STATEMENT}"; then
            MATCHING=true
            echo -e "Error occurred"
        fi
    fi
    if [[ ${MATCHING} == "true" ]]; then
        return 0
    else
        return 1
    fi
}

until line_matches_or_error "$1" || ((COUNT>29)); do
    echo "Log statement '$1' not present... attempt $COUNT"
    ((COUNT++))
    sleep 2
done

if (( COUNT >= 30 )); then
    echo "Statement $1 did not appear in time"
    exit 1
fi

exit 0