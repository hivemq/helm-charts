{{/*
Create a default fully qualified app name.
If release name contains chart name it will be used as a full name.
*/}}
{{- define "hivemq-platform-operator.name" -}}
{{- printf "%s-%s" "hivemq" .Release.Name }}
{{- end}}

{{/*
Common labels
*/}}
{{- define "hivemq-platform-operator.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{ include "hivemq-platform-operator.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
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

{{/*
Validates the PodSecurityContext values have no invalid combination.
Params:
- podSecurityContext: The .Values.nodes.podSecurityContext value.
Usage: {{- include "hivemq-platform-operator.validate-pod-security-context" (dict "podSecurityContext" .Values.podSecurityContext) }}
*/}}
{{- define "hivemq-platform-operator.validate-pod-security-context" -}}
{{- $podSecurityContext := .podSecurityContext }}
{{- if $podSecurityContext.enabled }}
    {{- if hasKey $podSecurityContext "runAsUser" }}
        {{- if and (eq $podSecurityContext.runAsNonRoot true) (eq ($podSecurityContext.runAsUser | toString) "0") }}
            {{- fail (printf "`runAsNonRoot` is set to `true` but `runAsUser` is set to `0` (root)") }}
        {{- end }}
        {{- if and (eq $podSecurityContext.runAsNonRoot false) (ne ($podSecurityContext.runAsUser | toString) "0") }}
            {{- fail (printf "`runAsNonRoot` is set to `false` but `runAsUser` is not set to `0` (root)") }}
        {{- end }}
    {{- end }}
{{- end }}
{{- end -}}
