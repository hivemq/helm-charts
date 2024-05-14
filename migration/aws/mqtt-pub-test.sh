#!/bin/bash

echo "Publishing a MQTT message with retained flag before migration"
mqtt pub -h localhost -p 1883 -t migration-topic -m hello-from-migration -r
trap 'echo "Disconnected from HiveMQ broker (localhost:1883)"' exit SIGTERM SIGINT
