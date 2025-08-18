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
Validates:
- No duplicated EnvVars defined in the `.Values.env` value.
- No default Operator EnvVar is set through `.Values.env`. Otherwise it clashes with the defaults already set.
Usage: {{ include "hivemq-platform-operator.validate-env-vars" . }}
*/}}
{{- define "hivemq-platform-operator.validate-env-vars" -}}
{{- include "hivemq-platform-operator.validate-duplicated-env-vars" . -}}
{{- include "hivemq-platform-operator.validate-default-operator-env-vars" . -}}
{{- end -}}

{{/*
Validates no default Operator EnvVar is set through `.Values.env`. Otherwise it clashes with the defaults already set.
Usage: {{- include "hivemq-platform-operator.validate-default-operator-env-vars" . }}
*/}}
{{- define "hivemq-platform-operator.validate-default-operator-env-vars" -}}
{{- $defaultEnvs := list
  "JAVA_OPTS"
  "HIVEMQ_PLATFORM_OPERATOR_NAMESPACES"
  "HIVEMQ_PLATFORM_OPERATOR_SELECTOR"
  "KUBERNETES_NAMESPACE"
  "HIVEMQ_PLATFORM_OPERATOR_RELEASE_NAME"
  "HIVEMQ_PLATFORM_OPERATOR_CRD_APPLY"
  "HIVEMQ_PLATFORM_OPERATOR_CRD_WAIT_UNTIL_READY"
  "HIVEMQ_PLATFORM_OPERATOR_CRD_WAIT_UNTIL_READY_TIMEOUT"
  "HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE"
  "HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE_PULL_POLICY"
  "HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE_RESOURCES_CPU"
  "HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE_RESOURCES_MEMORY"
  "HIVEMQ_PLATFORM_OPERATOR_INIT_IMAGE_RESOURCES_EPHEMERAL_STORAGE"
  "HIVEMQ_PLATFORM_OPERATOR_IMAGE_PULL_SECRET"
  "HIVEMQ_PLATFORM_OPERATOR_LOG_LEVEL"
  "HIVEMQ_PLATFORM_OPERATOR_LOG_CONFIGURATION"
  "HIVEMQ_PLATFORM_OPERATOR_PLATFORM_HEALTH_DETAILS_FORMAT"
  "HIVEMQ_PLATFORM_OPERATOR_RECONCILIATION_ROLLING_RESTART_CONCURRENT"
  "HIVEMQ_PLATFORM_OPERATOR_SERVICEACCOUNT_CREATE"
  "HIVEMQ_PLATFORM_OPERATOR_SERVICEACCOUNT_VALIDATE"
  "HIVEMQ_PLATFORM_OPERATOR_SERVICEACCOUNT_NAME"
  "HIVEMQ_PLATFORM_OPERATOR_SERVICEACCOUNT_PERMISSIONS_CREATE"
  "HIVEMQ_PLATFORM_OPERATOR_SERVICEACCOUNT_PERMISSIONS_VALIDATE"
  "HIVEMQ_PLATFORM_OPERATOR_SDK_LOG_LEVEL"
  "HIVEMQ_PLATFORM_OPERATOR_NETWORK_LOG_LEVEL"
  "HIVEMQ_PLATFORM_OPERATOR_SKIP_HTTPS_CERTIFICATE_VALIDATION"
  "HIVEMQ_PLATFORM_OPERATOR_SKIP_HTTPS_HOSTNAME_VERIFICATION"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_PORT"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_SSL_PORT"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_SSL_CERTIFICATE_KEY_STORE_FILE_TYPE"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_SSL_CERTIFICATE_KEY_STORE_FILE"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_SSL_CERTIFICATE_TRUST_STORE_FILE"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_SSL_CERTIFICATE_KEY_STORE_PASSWORD"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_SSL_CERTIFICATE_KEY_STORE_PRIVATE_KEY_PASSWORD"
  "HIVEMQ_PLATFORM_OPERATOR_HTTP_SSL_CERTIFICATE_TRUST_STORE_PASSWORD"
}}
{{- range .Values.env }}
  {{- if has .name $defaultEnvs }}
    {{- fail (printf "\nDefault environment variable `%s` for the HiveMQ Platform Operator is not allowed to be set via `.env` value. Please use the corresponding values instead." .name) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates there is no duplicated EnvVars defined in the `.Values.env` value.
Usage: {{- include "hivemq-platform-operator.validate-duplicated-env-vars" . }}
*/}}
{{- define "hivemq-platform-operator.validate-duplicated-env-vars" -}}
{{- $envList := list }}
{{- range .Values.env }}
  {{- if has .name $envList }}
    {{- fail (printf "\nDuplicated environment variable `%s` found in the `.Values.env` value." .name) }}
  {{- else }}
    {{- $envList = .name | append $envList}}
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
