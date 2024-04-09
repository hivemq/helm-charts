{{/*
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hivemq-platform.name" -}}
{{- printf "%s-%s" "hivemq" .Release.Name }}
{{- end -}}

{{- define "hivemq-platform.configuration-name" -}}
{{- if .Values.config.create -}}
{{- printf "%s-%s" "hivemq-configuration" .Release.Name }}
{{- else }}
    {{- if .Values.config.name -}}
    {{- printf "%s" .Values.config.name }}
    {{- else }}
        {{- fail ("HiveMQ configuration ConfigMap name cannot be empty when using an existing ConfigMap") }}
    {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "hivemq-platform.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "hivemq-platform.labels" -}}
helm.sh/chart: {{ include "hivemq-platform.chart" . }}
{{ include "hivemq-platform.selector-labels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "hivemq-platform.selector-labels" -}}
app.kubernetes.io/name: "hivemq-platform"
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}

{{/*
Has license
*/}}
{{- define "hivemq-platform.has-license" -}}
{{- $licenseExists := false }}
{{- if .Values.license }}
    {{- if or .Values.license.name (or .Values.license.overrideLicense .Values.license.data) }}
        {{- printf "found" }}
    {{- end }}
{{- end -}}
{{- end -}}

{{/*
Returns the default license name for the platform
*/}}
{{- define "hivemq-platform.default-license-name" -}}
{{- printf "%s-%s" "hivemq-license" .Release.Name }}
{{- end -}}

{{/*
Has additional volumes
*/}}
{{- define "hivemq-platform.has-additional-volumes" -}}
{{- if .Values.additionalVolumes }}
    {{- printf "true" }}
{{- end -}}
{{- end -}}

{{/*
Gets the port name inside a range of the service values
Format: <.Values.services>
Usage: {{ include "hivemq-platform.range-service-port-name" . }}
*/}}
{{- define "hivemq-platform.range-service-port-name" -}}
{{- if eq .type "control-center"}}
{{ printf "%s-%s" "cc" (toString .containerPort) }}
{{- else if eq .type "rest-api" }}
{{ printf "%s-%s" "rest" (toString .containerPort) }}
{{- else if eq .type "websocket" }}
{{ printf "%s-%s" "ws" (toString .containerPort) }}
{{- else if and (eq .type "mqtt") .keystoreSecretName }}
{{- printf "%s-%s" "mqtts" (toString .containerPort) }}
{{- else }}
{{- printf "%s-%s" .type (toString .containerPort) }}
{{- end -}}
{{- end -}}

{{/*
Get the service name inside a range of the service values
Format: hivemq-<.Release.Name>-<.Values.services.type>-<.Values.services.port | .Values.services.containerPort>
Usage: {{ include "hivemq-platform.range-service-name" (dict "releaseName" $.Release.Name "type" .type "port" .port "containerPort" .containerPort "keystoreSecretName" .keystoreSecretName) }}
*/}}
{{- define "hivemq-platform.range-service-name" -}}
{{- $type := "" }}
{{- $port := (toString .containerPort) }}
{{- if eq .type "control-center"}}
{{- $type = "cc" }}
{{- else if eq .type "rest-api" }}
{{- $type = "rest" }}
{{- else if eq .type "websocket" }}
{{- $type = "ws" }}
{{- else if and (eq .type "mqtt") .keystoreSecretName }}
{{- $type = "mqtts" }}
{{- else }}
{{- $type = .type }}
{{- end -}}
{{- if (.port) }}
{{- $port = (toString .port) }}
{{- end }}
{{- printf "hivemq-%s-%s-%s" (trunc 42 .releaseName | trimSuffix "-") $type $port }}
{{- end -}}

{{/*
Checks if a particular service type exists and is exposed within the services values.
Params:
- services: The array of services to check.
- expectedType: The expected type to check for.
Returns:
- `true` if the desired type is found and the service is marked as `exposed`, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-service-type" -}}
{{- $services := .services }}
{{- $expectedType := .expectedType }}
{{- $typeExists := "" }}
{{- range $service := $services }}
  {{- if and $service.exposed (eq $service.type $expectedType) }}
    {{- $typeExists = true }}
    {{- break }}
  {{- end }}
{{- end }}
{{- $typeExists }}
{{- end -}}

{{/*
Returns the placeholder name to be used by the `config.xml` file for the private key password used by the TLS listeners.
This can only be used within a range of service values.
Format: <.Values.services.type>_<.Release.Name>_<.Values.services.keystoreSecretName>_<keystore_pass | keystore_private_pass>
Usage: {{ include "hivemq-platform.keystore-private-password" (dict "releaseName" $.Release.Name "type" .type "keystoreSecretName" .keystoreSecretName "keystorePrivatePassword" .keystorePrivatePassword "keystorePrivatePasswordSecretKey" .keystorePrivatePasswordSecretKey) }}
*/}}
{{- define "hivemq-platform.keystore-private-password" -}}
{{- if or .keystorePrivatePassword .keystorePrivatePasswordSecretKey }}
{{- printf "%s_%s_%s_%s" .type .releaseName .keystoreSecretName "keystore_private_pass" -}}
{{- else }}
{{- printf "%s_%s_%s_%s" .type .releaseName .keystoreSecretName "keystore_pass" -}}
{{- end }}
{{- end }}

{{/*
Gets the default Operator REST API port value.
Usage: {{ include "hivemq-platform.operator-rest-api-port" . }}
*/}}
{{- define "hivemq-platform.operator-rest-api-port" -}}
{{- 7979 }}
{{- end -}}

{{/*
Gets the default Health API port value.
Usage: {{ include "hivemq-platform.health-api-port" . }}
*/}}
{{- define "hivemq-platform.health-api-port" -}}
{{- 8889 }}
{{- end -}}

{{/*
Gets the default metrics port value.
Usage: {{ include "hivemq-platform.metrics-port" . }}
*/}}
{{- define "hivemq-platform.metrics-port" -}}
{{- 9399 }}
{{- end -}}

{{/*
Gets the default cluster transport port value.
Usage: {{ include "hivemq-platform.cluster-transport-port" . }}
*/}}
{{- define "hivemq-platform.cluster-transport-port" -}}
{{- 7000 }}
{{- end -}}

{{/*
Validates all the exposed services.
- No duplicated ports are defined as part of the `containerPort` values
- No default ports (7979, 8889, 9399, 7000) are defined as part of the `containerPort` values
Params:
- services: The array of services to check.
Usage: {{ include "hivemq-platform.validate-service-ports" (dict "services" .Values.services) }}
*/}}
{{- define "hivemq-platform.validate-service-ports" -}}
{{- $services := .services }}
{{- include "hivemq-platform.validate-duplicated-service-ports" (dict "services" $services) -}}
{{- include "hivemq-platform.validate-default-service-ports" (dict "services" $services) -}}
{{- end -}}

{{/*
Validates there is no duplicated `containerPort` defined for the exposed services.
Params:
- services: The array of services to check.
Usage: {{ include "hivemq-platform.validate-duplicated-service-ports" (dict "services" .Values.services) }}
*/}}
{{- define "hivemq-platform.validate-duplicated-service-ports" -}}
{{- $services := .services }}
{{- $containerPortsList := list }}
{{- range $service := $services }}
  {{- if and $service.exposed (has (int64 $service.containerPort) $containerPortsList) }}
    {{- fail (printf "Container port %d in service `%s` already exists" (int64 $service.containerPort) $service.type) }}
  {{- end }}
  {{- $containerPortsList = (int64 $service.containerPort) | append $containerPortsList}}
{{- end }}
{{- end -}}

{{/*
Validates there is no default `containerPort` (7979, 8889, 9399, 7000) defined as part of the exposed services ports.
Params:
- services: The array of services to check.
Usage: {{ include "hivemq-platform.validate-default-service-ports" (dict "services" .Values.services) }}
*/}}
{{- define "hivemq-platform.validate-default-service-ports" -}}
{{- $services := .services }}
{{- $defaultPortsList := list }}
{{- $defaultPortsList := ( include "hivemq-platform.operator-rest-api-port" . | int64 ) | append $defaultPortsList }}
{{- $defaultPortsList := ( include "hivemq-platform.health-api-port" . | int64 ) | append $defaultPortsList }}
{{- $defaultPortsList := ( include "hivemq-platform.metrics-port" . | int64 ) | append $defaultPortsList }}
{{- $defaultPortsList := ( include "hivemq-platform.cluster-transport-port" . | int64 ) | append $defaultPortsList }}
{{- range $service := $services }}
  {{- if and $service.exposed (has (int64 $service.containerPort) $defaultPortsList) }}
    {{- fail (printf "Container port %d in service `%s` already exists as part of one of the predefined ports (%s)" (int64 $service.containerPort) $service.type (join ", " $defaultPortsList)) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Check if there are services exposed with keystore
*/}}
{{- define "hivemq-platform.has-keystore" -}}
{{- range $key, $val := .Values.services }}
    {{- if and $val.exposed $val.keystoreSecretName }}
        {{- printf "found" }}
        {{- break }}
    {{- end }}
{{- end -}}
{{- end -}}

{{/*
Get secret volume mount
 - Only Add truststore if secret is set
*/}}
{{- define "hivemq-platform.get-tls-volume-mount" -}}
{{- $secretsNames := list }}
{{- range $service := .Values.services }}
  {{- if and $service.exposed (and $service.keystoreSecretName (not (has $service.keystoreSecretName $secretsNames))) }}
    {{- $secretsNames = $service.keystoreSecretName | append $secretsNames}}
  {{- end -}}
  {{- if and (and $service.exposed $service.keystoreSecretName) $service.truststoreSecretName }}
    {{- if not (has $service.truststoreSecretName $secretsNames) }}
      {{- $secretsNames = $service.truststoreSecretName | append $secretsNames}}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- range $name := $secretsNames }}
- name: {{$name}}
  mountPath: /tls-{{$name}}
  readOnly: true
{{- end -}}
{{- end -}}

{{/*
Get the container volume mounts
*/}}
{{- define "hivemq-platform.get-additional-volume-mounts" -}}
{{- range $volume := .Values.additionalVolumes }}
{{- if $volume.mountName }}
- name: {{ $volume.mountName }}
{{- else }}
- name: {{ $volume.name }}
{{- end}}
  {{- if $volume.subPath }}
  mountPath: {{ $volume.path }}/{{ $volume.subPath }}
  subPath: {{ $volume.subPath }}
  {{- else }}
  mountPath: {{ $volume.path }}
  {{- end }}
{{- end -}}
{{- end -}}

{{/*
Checks if there is any HiveMQ restriction options based on the .Values.hivemqRestrictions values
Params:
- hivemqRestrictions: The set of values from hivemqRestrictions
Returns:
- `true` if any of the expected values under the hivemqRestrictions top level root value is present, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-hivemq-restrictions-config" }}
{{- $restrictionsConfig := .hivemqRestrictions }}
{{- $contains := "" }}
{{- if or
    (hasKey $restrictionsConfig "maxConnections")
    (hasKey $restrictionsConfig "incomingBandwidthThrottling")
    (hasKey $restrictionsConfig "noConnectIdleTimeout")
    (hasKey $restrictionsConfig "maxClientIdLength") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Checks if there is any HiveMQ MQTT options based on the .Values.hivemqMqtt values
Params:
- hivemqMqtt: The set of values from hivemqMqtt
Returns:
- `true` if any of the expected values under the hivemqMqtt top level root value is present, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-hivemq-mqtt-config" }}
{{- $mqttConfig := .hivemqMqtt }}
{{- $contains := "" }}
{{- if or
    (hasKey $mqttConfig "sessionExpiryMaxInterval")
    (hasKey $mqttConfig "messageExpiryMaxInterval")
    (hasKey $mqttConfig "maxPacketSize")
    (hasKey $mqttConfig "serverReceiveMaximum")
    (hasKey $mqttConfig "keepAliveMax")
    (hasKey $mqttConfig "keepAliveAllowUnlimited")
    (hasKey $mqttConfig "topicAliasEnabled")
    (hasKey $mqttConfig "topicAliasMaxPerClient")
    (hasKey $mqttConfig "subscriptionIdentifier")
    (hasKey $mqttConfig "wildcardSubscriptions")
    (hasKey $mqttConfig "sharedSubscriptions")
    (hasKey $mqttConfig "maxQualityOfService")
    (hasKey $mqttConfig "retainedMessages")
    (hasKey $mqttConfig "queuedMessagesMaxSize")
    (hasKey $mqttConfig "queuedMessagesStrategy") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Checks if there is any HiveMQ MQTT Add-on options based on the .Values.hivemqMqttAddons values
Params:
- hivemqMqttAddons: The set of values from hivemqMqttAddons
Returns:
- `true` if any of the expected values under the hivemqMqttAddons top level root value is present, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-hivemq-mqtt-addons-config" }}
{{- $mqttAddonsConfig := .hivemqMqttAddons }}
{{- $contains := "" }}
{{- if or
    (hasKey $mqttAddonsConfig "expiredMessagesTopic")
    (hasKey $mqttAddonsConfig "droppedMessagesTopic")
    (hasKey $mqttAddonsConfig "deadMessagesTopic") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Checks if there is any HiveMQ MQTT security options based on the .Values.hivemqMqttSecurity values
Params:
- hivemqMqttSecurity: The set of values from hivemqMqttSecurity
Returns:
- `true` if any of the expected values under the hivemqMqttSecurity top level root value is present, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-hivemq-mqtt-security-config" }}
{{- $mqttSecurityConfig := .hivemqMqttSecurity }}
{{- $contains := "" }}
{{- if or
    (hasKey $mqttSecurityConfig "allowEmptyClientId")
    (hasKey $mqttSecurityConfig "payloadFormatValidation")
    (hasKey $mqttSecurityConfig "utf8Validation")
    (hasKey $mqttSecurityConfig "allowRequestProblemInformation")
    (hasKey $mqttSecurityConfig "controlCenterAuditLog") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Get the volumes references
*/}}
{{- define "hivemq-platform.get-additional-volumes" -}}
{{- range $volume := .Values.additionalVolumes -}}
{{- if $volume.mountName }}
- name: {{ $volume.mountName }}
{{- else }}
- name: {{ $volume.name }}
{{- end }}
  {{- if eq $volume.type "configMap" }}
  configMap:
    name: {{ $volume.name }}
  {{- else if eq $volume.type "secret" }}
  secret:
    secretName: {{ $volume.name }}
  {{- else if eq $volume.type "emptyDir" }}
  emptyDir: {}
  {{- else if eq $volume.type "persistentVolumeClaim" }}
  persistentVolumeClaim:
    claimName: {{ $volume.name }}
  {{- else }}
    {{- fail ("Invalid type or not supported type for additional volume") }}
  {{- end }}
{{- end -}}
{{- end -}}

{{/*
Get secret volume mount
- Only add truststore if secret is set
*/}}
{{- define "hivemq-platform.get-tls-secret-volumes" -}}
{{- $secretNames := list }}
{{- range $service := .Values.services }}
  {{- if and $service.exposed (and $service.keystoreSecretName (not (has $service.keystoreSecretName $secretNames))) }}
    {{- $secretNames = $service.keystoreSecretName | append $secretNames}}
  {{- end -}}
  {{- if and (and $service.exposed $service.keystoreSecretName) $service.truststoreSecretName }}
    {{- if not (has $service.truststoreSecretName $secretNames) }}
      {{- $secretNames = $service.truststoreSecretName | append $secretNames }}
    {{- end -}}
  {{- end -}}
{{- end -}}
{{- range $name := $secretNames }}
- name: {{ $name}}
  secret:
    secretName: {{ $name }}
{{- end }}
{{- end -}}
