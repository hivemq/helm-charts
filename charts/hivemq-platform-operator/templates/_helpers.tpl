{{/*
Creates a qualified name, based on the release name.
Params:
- prefix:       The custom prefix to prepend to the name. Otherwise, defaults to `hivemq`.
- name:         The custom name to append to the default prefix `hivemq-platform-operator`.
- releaseName:  The .Release.Name value.
Returns:
- The resource name based on the custom prefix, custom name and on the release name with a default prefix of `hivemq`.
Format: <custom-prefix>-<custom-name>-<.Release.Name> | "hivemq-"-<.Release.Name>
Usage: {{ include "hivemq-platform-operator.name" (dict "prefix" "my-custom-prefix" "name" "my-custom-name" "releaseName" .Release.Name) }}
*/}}
{{- define "hivemq-platform-operator.name" -}}
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
{{- define "hivemq-platform-operator.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{ include "hivemq-platform-operator.selector-labels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "hivemq-platform-operator.selector-labels" -}}
app.kubernetes.io/name: "hivemq-platform-operator"
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}

{{/*
Creates the name of the service account to use for the HiveMQ Platform Operator
*/}}
{{- define "hivemq-platform-operator.serviceAccountName" -}}
{{- if .Values.serviceAccount.name }}
{{- printf "%s" .Values.serviceAccount.name }}
{{- else }}
{{- include "hivemq-platform-operator.name" (dict "prefix" "hivemq-platform-operator" "releaseName" .Release.Name) }}
{{- end }}
{{- end -}}

{{/*
Creates the HiveMQ Platform Operator HTTP service port name.
Usage: {{ include "hivemq-platform-operator.http-service-port-name" . }}
*/}}
{{- define "hivemq-platform-operator.http-service-port-name" -}}
{{ printf "http-%s" .Release.Name }}
{{- end -}}

{{/*
Creates the HiveMQ Platform Operator HTTP container port name.
Usage: {{ include "hivemq-platform-operator.http-container-port-name" . }}
*/}}
{{- define "hivemq-platform-operator.http-container-port-name" -}}
{{ printf "http-%s" .Release.Name | lower | trimAll "-" | trunc 15 | trimSuffix "-" | trim }}
{{- end -}}

{{/*
Creates the HiveMQ Platform Operator HTTPs service port name.
Usage: {{ include "hivemq-platform-operator.https-service-port-name" . }}
*/}}
{{- define "hivemq-platform-operator.https-service-port-name" -}}
{{ printf "https-%s" .Release.Name }}
{{- end -}}

{{/*
Creates the HiveMQ Platform Operator HTTPs container port name.
Usage: {{ include "hivemq-platform-operator.https-container-port-name" . }}
*/}}
{{- define "hivemq-platform-operator.https-container-port-name" -}}
{{ printf "https-%s" .Release.Name | lower | trimAll "-" | trunc 15 | trimSuffix "-" | trim }}
{{- end -}}

{{/*
Validates `runAsNonRoot` and `runAsUser` has a valid combination for the PodSecurityContext or SecurityContext.
Params:
- securityContext: Either `.Values.podSecurityContext` or `.Values.containerSecurityContext` values.
Usage: {{- include "hivemq-platform-operator.validate-run-as-user-security-context" .Values.podSecurityContext }}
*/}}
{{- define "hivemq-platform-operator.validate-run-as-user-security-context" -}}
{{- $securityContext := . -}}
{{- if and (hasKey $securityContext "runAsNonRoot") (hasKey $securityContext "runAsUser") }}
    {{- if and (eq $securityContext.runAsNonRoot true) (eq ($securityContext.runAsUser | toString) "0") }}
        {{- fail (printf "\n`runAsNonRoot` is set to `true` but `runAsUser` is set to `0` (root)") }}
    {{- end }}
    {{- if and (eq $securityContext.runAsNonRoot false) (ne ($securityContext.runAsUser | toString) "0") }}
        {{- fail (printf "\n`runAsNonRoot` is set to `false` but `runAsUser` is not set to `0` (root)") }}
    {{- end }}
{{- end }}
{{- end -}}

{{/*
Generates the runAsNonRoot, runAsUser and runAsGroup fields for the PodSecurityContext or the SecurityContext.
Params:
- securityContext: Either `.Values.podSecurityContext` or `.Values.containerSecurityContext` values.
- indentation: Number of spaces to use for the indentation.
Usage: {{ include "hivemq-platform-operator.generate-run-as-security-context" (dict "securityContext" .Values.podSecurityContext "indentation" 12) }}
*/}}
{{- define "hivemq-platform-operator.generate-run-as-security-context" -}}
{{- $securityContext := .securityContext -}}
{{- $indentation := .indentation -}}
{{- if hasKey $securityContext "runAsNonRoot" -}}
    {{- printf "runAsNonRoot: %v" $securityContext.runAsNonRoot | nindent $indentation -}}
    {{- if eq $securityContext.runAsNonRoot false -}}
        {{- printf "runAsUser: 0" | nindent $indentation -}}
    {{- else if hasKey $securityContext "runAsUser" -}}
        {{- printf "runAsUser: %v" $securityContext.runAsUser | nindent $indentation -}}
    {{- else -}}
        {{- printf "runAsUser: 185" | nindent $indentation -}}
    {{- end -}}
    {{- printf "runAsGroup: %v" ($securityContext.runAsGroup | default 0) | nindent $indentation -}}
{{- else  -}}
    {{- if hasKey $securityContext "runAsUser" -}}
        {{- printf "runAsUser: %v" $securityContext.runAsUser | nindent $indentation -}}
    {{- end -}}
    {{- if hasKey $securityContext "runAsGroup" -}}
        {{- printf "runAsGroup: %v" $securityContext.runAsGroup | default 0 | nindent $indentation -}}
    {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Validates that the required Prometheus Monitoring CRDs are installed in the Kubernetes cluster.
If the required CRDs are not present the ServiceMonitor cannot be installed and the installation fails.
*/}}
{{- define "hivemq-platform-operator.validate-prometheus-monitoring-stack-installed" -}}
{{- $isCRDPresent := .Capabilities.APIVersions.Has "monitoring.coreos.com/v1" }}
{{- if not $isCRDPresent }}
    {{- fail (printf "\nThere is no Prometheus ServiceMonitor CustomResourceDefinition (CRD) available in your Kubernetes cluster. Prometheus Monitoring CRDs are required before installing the ServiceMonitor resource.\nCheck out https://docs.hivemq.com/hivemq-platform-operator/observability.html#monitoring for more help and guidance.") }}
{{- end }}
{{- end }}
