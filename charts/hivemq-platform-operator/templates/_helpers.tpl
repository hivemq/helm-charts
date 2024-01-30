{{/*
Create a default fully qualified app name.
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hivemq-platform-operator.name" -}}
{{- printf "%s-%s" "hivemq" .Release.Name }}
{{- end}}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "hivemq-platform-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "hivemq-platform-operator.labels" -}}
helm.sh/chart: {{ include "hivemq-platform-operator.chart" . }}
{{ include "hivemq-platform-operator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "hivemq-platform-operator.selectorLabels" -}}
app.kubernetes.io/name: "hivemq-platform-operator"
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end }}

{{/*
Create the name of the service account to use for the HiveMQ Platform Operator
*/}}
{{- define "hivemq-platform-operator.serviceAccountName" -}}
{{- if .Values.serviceAccount.name }}
{{- printf "%s" .Values.serviceAccount.name }}
{{- else }}
{{- printf "%s-%s" "hivemq-platform-operator" .Release.Name }}
{{- end }}
{{- end }}
