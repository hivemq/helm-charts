{{/*
Creates a qualified name, based on the release name.
Params:
- prefix:       The custom prefix to prepend to the name. Otherwise, defaults to `hivemq`.
- name:         The custom name to append to the default prefix `hivemq-edge`.
- releaseName:  The .Release.Name value.
Returns:
- The resource name based on the custom prefix, custom name and on the release name with a default prefix of `hivemq`.
Format: <custom-prefix>-<custom-name>-<.Release.Name> | "hivemq-"-<.Release.Name>
Usage: {{ include "hivemq-edge.name" (dict "prefix" "my-custom-prefix" "name" "my-custom-name" "releaseName" .Release.Name) }}
*/}}
{{- define "hivemq-edge.name" -}}
{{- $customPrefix := .prefix }}
{{- $customName := .name }}
{{- $releaseName := .releaseName }}
{{- $prefix := "hivemq" }}
{{- $name := "" }}
{{- if $customName -}}
{{- $name = printf "%s-%s-%s" ($customPrefix | default $prefix) $customName $releaseName }}
{{- else -}}
{{- $name = printf "%s-%s" ($customPrefix | default $prefix) $releaseName }}
{{- end -}}
{{- printf "%s" $name | trimAll "-" | trunc 63 | trimSuffix "-" | trim }}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "hivemq-edge.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{ include "hivemq-edge.selector-labels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "hivemq-edge.selector-labels" -}}
app.kubernetes.io/name: "hivemq-edge"
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}
