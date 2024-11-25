{{/*
Expands the name of the chart.
*/}}
{{- define "hivemq-swarm.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Creates a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hivemq-swarm.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Creates chart name and version as used by the chart label.
*/}}
{{- define "hivemq-swarm.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "hivemq-swarm.labels" -}}
helm.sh/chart: {{ include "hivemq-swarm.chart" . }}
{{ include "hivemq-swarm.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels commander
*/}}
{{- define "hivemq-swarm.selectorLabels" -}}
app.kubernetes.io/name: {{ include "hivemq-swarm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Creates the name of the service account to use
*/}}
{{- define "hivemq-swarm.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "hivemq-swarm.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "hivemq-swarm.namespace" -}}
  {{- if .Values.namespaceOverride -}}
    {{- .Values.namespaceOverride -}}
  {{- else -}}
    {{- .Release.Namespace -}}
  {{- end -}}
{{- end -}}

{{/*
Validates that the required Prometheus Monitoring CRDs are installed in the Kubernetes cluster.
If the required CRDs is not present the ServiceMonitor cannot be installed and the installation fails.
*/}}
{{- define "hivemq-swarm.validate-prometheus-monitoring-stack-installed" -}}
{{- $isCRDPresent := .Capabilities.APIVersions.Has "monitoring.coreos.com/v1" }}
{{- if not $isCRDPresent }}
    {{- fail (printf "\nThere is no Prometheus ServiceMonitor CustomResourceDefinition (CRD) available in your Kubernetes cluster. Prometheus Monitoring CRDs are required before installing the ServiceMonitor resource.\nCheck out https://docs.hivemq.com/hivemq-swarm/latest/clustering.html#monitor for more help and guidance.") }}
{{- end }}
{{- end }}
