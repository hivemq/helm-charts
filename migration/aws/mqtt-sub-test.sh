#!/bin/bash

echo "Subscribing to a MQTT topic with retained flag after migration"
mqtt sub -h localhost -p 1883 -t migration-topic -J
trap 'echo "Disconnected from HiveMQ broker (localhost:1883)"' exit SIGTERM SIGINT
