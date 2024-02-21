{{/*
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hivemq-platform.name" -}}
{{- printf "%s-%s" "hivemq" .Release.Name }}
{{- end -}}

{{- define "hivemq-platform.configuration-name" -}}
{{- if .Values.config.create -}}
"hivemq-configuration-{{ .Release.Name}}"
{{- else}}
    {{- if .Values.config.name -}}
    {{.Values.config.name}}
    {{- else}}
    fail ("HiveMQ configuration ConfigMap name cannot be empty when using an existing ConfigMap")
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
Has additional volumes
*/}}
{{- define "hivemq-platform.has-additional-volumes" -}}
{{- if .Values.additionalVolumes }}
    {{- printf "true" }}
{{- end -}}
{{- end -}}

{{/*
Get the port name inside a range of the service values
Format: <.Values.services.type>
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
Usage: {{ include "hivemq-platform.range-service-name" (dict "releaseName" $.Release.Name "type" .type "port" .port "containerPort" .containerPort) }}
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
Check if there are services exposed with keystore
*/}}
{{- define "hivemq-platform.has-keystore" -}}
{{- range $key, $val := .Values.services }}
    {{- if and $val.exposed $val.keystoreSecretName }}
        {{- if or $val.keystorePassword $val.keystorePasswordSecretName }}
        {{- printf "found" }}
        {{- break }}
        {{- else }}
        fail (printf "A keystore password should be set either as a string or as a secret name")
        {{- end }}
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
Get the volumes references
*/}}
{{- define "hivemq-platform.get-additional-volumes" -}}
{{- range $volume := .Values.additionalVolumes -}}
{{- if $volume.mountName }}
- name: {{ $volume.mountName }}
{{- else }}
- name: {{ $volume.name }}
{{- end}}
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
  fail ("Invalid type or not supported type for additional volume")
  {{- end }}
{{- end -}}
{{- end -}}

{{/*
Get secret volume mount
- Only Add truststore if secret is set
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
