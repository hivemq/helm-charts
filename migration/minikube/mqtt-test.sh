#!/bin/bash

echo "Publishing a MQTT message with retained flag before migration"
mqtt pub -h localhost -p 1883 -t migration-topic -m hello-from-migration -r
mqtt sub -h localhost -p 1883 -t migration-topic -J
#read -n 1 -r -s -p "Press any key to continue..."
#echo
#echo "Continuing with the script..."
#exit 0

trap 'echo "Disconnected from HiveMQ broker (localhost:1883)"' EXIT
