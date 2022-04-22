#!/usr/bin/env bash

# Create a heap dump to /opt/hivemq/heap-dump.hprof

curl -L https://github.com/apangin/jattach/releases/download/v1.5/jattach > jattach
chmod +x jattach
./jattach 1 dumpheap heap-dump.hprof

