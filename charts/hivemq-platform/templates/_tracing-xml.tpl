{{/*
Generates a default HiveMQ tracing.xml content
Usage: {{ include "hivemq-platform.default-hivemq-tracing-configuration" . }}
*/}}
{{- define "hivemq-platform.default-hivemq-tracing-configuration" -}}
<?xml version="1.0" encoding="UTF-8" ?>
<tracing xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="tracing.xsd">
  <context-propagation>
    <outbound-context-propagation>
      <enabled>false</enabled>
    </outbound-context-propagation>
  </context-propagation>
  <sampling>
    <publish-sampling>
      <enabled>true</enabled>
    </publish-sampling>
  </sampling>
</tracing>
{{- end -}}
