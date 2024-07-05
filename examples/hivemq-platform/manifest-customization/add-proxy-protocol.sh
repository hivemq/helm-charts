#!/bin/sh

# variables
CONFIGMAP_FILE="hivemq-configuration.yml"
TMP_CONFIG_XML_FILE="tmp_config.xml"
START_MARKER="config.xml:"
END_MARKER="tracing.xml:"

cd deployment/hivemq-platform || exit 1

# extract config.xml from ConfigMap file
yq e '.data["config.xml"]' $CONFIGMAP_FILE > $TMP_CONFIG_XML_FILE

# modify the config.xml (add the tag <proxy-protocol>true</proxy-protocol> to the TCP listener with port 1884)
xmlstarlet ed -L -s "//tcp-listener[port='1884']" -t elem -n proxy-protocol -v "true" $TMP_CONFIG_XML_FILE

# replace the config.xml section in the ConfigMap file
sed -i 's/^/    /' $TMP_CONFIG_XML_FILE
sed -i "/$START_MARKER/,/$END_MARKER/{ /$START_MARKER/{p; r $TMP_CONFIG_XML_FILE
}; /$END_MARKER/p; d }" $CONFIGMAP_FILE

# cleanup
rm $TMP_CONFIG_XML_FILE
