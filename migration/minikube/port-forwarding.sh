#!/bin/bash

nohup kubectl port-forward services/hivemq-hivemq-mqtt 1883:1883 -n migration > /dev/null 2>&1 &
portForwardPid=$!
