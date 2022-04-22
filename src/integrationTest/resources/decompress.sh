#!/usr/bin/env sh
/bin/gunzip -c /opt/hivemq-image.tgz > /opt/hivemq-image.tar
/bin/ctr images import /opt/hivemq-image.tar