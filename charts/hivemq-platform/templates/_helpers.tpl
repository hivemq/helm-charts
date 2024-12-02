{{/*
Creates a qualified name, based on the release name.
Params:
- name: The custom name to append to the default prefix `hivemq-platform-operator`.
- releaseName: The .Release.Name value.
Returns:
- The resource name based on the custom name argument and on the release name with a default prefix of `hivemq-platform`.
Format: "hivemq-platform"-<custom-name>-<.Release.Name> | "hivemq-platform"-<.Release.Name>
Usage: {{ include "hivemq-platform.name" (dict "name" "my-custom-name" "releaseName" .Release.Name) }}
*/}}
{{- define "hivemq-platform.name" -}}
{{- $customName := .name }}
{{- $releaseName := .releaseName }}
{{- $prefix := "hivemq-platform" }}
{{- $name := "" }}
{{- if $customName -}}
{{- $name = printf "%s-%s-%s" $prefix $customName $releaseName }}
{{- else -}}
{{- $name = printf "%s-%s" $prefix $releaseName }}
{{- end -}}
{{- printf "%s" $name | trimAll "-" | trunc 63 | trimSuffix "-" | trim }}
{{- end -}}

{{/*
Returns a string containing the HiveMQ configuration ConfigMap or Secret name, depending on the `.Values.config.create` value.
It will return the default ConfigMap or Secret name for the HiveMQ Platform configuration or will reuse the ConfigMap or Secret name defined in the `.Values.config.name` if present.
Otherwise, an validation error will displayed.
Usage: {{ include "hivemq-platform.configuration-name" . }}
*/}}
{{- define "hivemq-platform.configuration-name" -}}
{{- if .Values.config.create -}}
{{- printf "%s-%s" "hivemq-configuration" .Release.Name }}
{{- else }}
    {{- if .Values.config.name -}}
    {{- printf "%s" .Values.config.name }}
    {{- else }}
        {{- $kind := .Values.config.createAs }}
        {{- fail (printf "\nHiveMQ configuration %s name cannot be empty when using an existing %s" $kind $kind) }}
    {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Common labels
*/}}
{{- define "hivemq-platform.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{ include "hivemq-platform.selector-labels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
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
Checks whether a license is in use by the Platform.
Returns:
- `true` if it's reusing an existing license created in a separate Secret or if it's creating a new one. Empty string otherwise.
Usage: {{ include "hivemq-platform.has-license" . }}
*/}}
{{- define "hivemq-platform.has-license" -}}
{{- $licenseExists := "" -}}
{{- if or (and (not .Values.license.create) .Values.license.name) .Values.license.create -}}
    {{- $licenseExists = true -}}
{{- end -}}
{{- $licenseExists -}}
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
Does the `hivemq` container have additional volume mounts
Returns:
- `true` if the main `hivemq` container is not explicitily set in any element of the `.Values.additionalVolumes` array
    as a `.Values.additionalVolumes.containerName` (default value), empty string otherwise.
Usage: {{ include "hivemq-platform.has-additional-volume-mounts" . }}
*/}}
{{- define "hivemq-platform.has-additional-volume-mounts" -}}
{{- $hasAdditionalVolumeMount := "" }}
{{- range $additionalVolume := .Values.additionalVolumes }}
  {{- if or (not (hasKey $additionalVolume "containerName")) ( and (hasKey $additionalVolume "containerName") (eq $additionalVolume.containerName "hivemq")) }}
    {{- $hasAdditionalVolumeMount = true }}
    {{- break }}
  {{- end }}
{{- end -}}
{{- $hasAdditionalVolumeMount }}
{{- end -}}

{{/*
Returns the service port name inside a range of the service values, truncating the name up to 63 characters (no truncation
for legacy services).
Legacy format: <.Values.services.legacyPortName>
Platform format: <.Values.services.type>-<.Values.services.containerPort>
Usage: {{ include "hivemq-platform.service-port-name" . }}
*/}}
{{- define "hivemq-platform.service-port-name" -}}
{{- if .legacyPortName }}
  {{- printf "%s" .legacyPortName | trim }}
{{- else }}
  {{- $type := "mqtt" }}
  {{- if eq .type "control-center" }}
    {{- $type = "cc" }}
  {{- else if eq .type "rest-api" }}
    {{- $type = "rest" }}
  {{- else if eq .type "websocket" }}
    {{- $type = "ws" }}
  {{- else if eq .type "metrics" }}
    {{- $type = "metrics" }}
  {{- else if .keystoreSecretName }}
    {{- $type = "mqtts" }}
  {{- end -}}
  {{- printf "%s-%s" $type (toString .containerPort) | trimAll "-" | trunc 63 | trimSuffix "-" | trim }}
{{- end -}}
{{- end -}}

{{/*
Returns the container port name inside a range of the service values, truncating the name up to 15 characters (no truncation
for legacy services).
Legacy format: <.Values.services.legacyPortName>
Platform format: <.Values.services.type>-<.Values.services.containerPort>
Usage: {{ include "hivemq-platform.container-port-name" . }}
*/}}
{{- define "hivemq-platform.container-port-name" -}}
{{- if .legacyPortName }}
  {{- printf "%s" .legacyPortName | trim }}
{{- else }}
  {{- $type := "mqtt" }}
  {{- if eq .type "control-center" }}
    {{- $type = "cc" }}
  {{- else if eq .type "rest-api" }}
    {{- $type = "rest" }}
  {{- else if eq .type "websocket" }}
    {{- $type = "ws" }}
  {{- else if eq .type "metrics" }}
    {{- $type = "metrics" }}
  {{- else if .keystoreSecretName }}
    {{- $type = "mqtts" }}
  {{- end -}}
  {{- printf "%s-%s" $type (toString .containerPort) | trimAll "-" | trunc 15 | trimSuffix "-" | trim }}
{{- end -}}
{{- end -}}

{{/*
Get the service name inside a range of the service values. It truncates the service name to 63 characters (no truncation for
legacy services).
Legacy format: hivemq-<.Release.Name>-<.Values.services.type>-<.Values.services.port | .Values.services.containerPort>
Platform format: hivemq-<.Release.Name>-<.Values.services.legacyPortName>
Usage: {{ include "hivemq-platform.range-service-name" (dict "name" .name "releaseName" $.Release.Name "type" .type "port" .port "containerPort" .containerPort "legacyPortName" .legacyPortName) }}
*/}}
{{- define "hivemq-platform.range-service-name" -}}
{{- if .name }}
  {{- printf "%s" .name }}
{{- else if .legacyPortName }}
  {{- printf "hivemq-%s-%s" .releaseName .legacyPortName | trim }}
{{- else }}
  {{- $type := "mqtt" }}
  {{- $port := (toString .containerPort) }}
  {{- if eq .type "control-center" }}
    {{- $type = "cc" }}
  {{- else if eq .type "rest-api" }}
    {{- $type = "rest" }}
  {{- else if eq .type "websocket" }}
    {{- $type = "ws" }}
  {{- else if eq .type "metrics" }}
    {{- $type = "metrics" }}
  {{- else if .keystoreSecretName }}
    {{- $type = "mqtts" }}
  {{- end -}}
  {{- if (.port) }}
    {{- $port = (toString .port) }}
  {{- end }}
  {{- printf "hivemq-%s-%s-%s" (trunc 42 .releaseName | trimSuffix "-") $type $port | trimAll "-" | trunc 63 | trimSuffix "-" | trim }}
{{- end }}
{{- end -}}

{{/*
Checks if a particular service type exists and is exposed within the services values.
Params:
- services: The array of services to check.
- expectedType: The expected type to check for.
Returns:
- `true` if the desired type is found and the service is marked as `exposed`, empty string otherwise.
Usage: {{ include "hivemq-platform.has-service-type" (dict "services" .Values.services "expectedType" "mqtt") }}
*/}}
{{- define "hivemq-platform.has-service-type" -}}
{{- $services := .services }}
{{- $expectedType := .expectedType }}
{{- $typeExists := "" }}
{{- range $service := $services }}
  {{- if and ($service.exposed) (eq $service.type $expectedType) }}
    {{- $typeExists = true }}
    {{- break }}
  {{- end }}
{{- end }}
{{- $typeExists }}
{{- end -}}

{{/*
Checks if there is default metrics service available as part of the `.Values.services` based on the `.Values.metrics.port` used,
regardless of whether it is exposed or not.
Params:
- services: The array of services to check.
Returns:
- `true` if a default `metrics` service is found, empty string otherwise.
Usage: {{ include "hivemq-platform.has-default-metrics-service" . }}
*/}}
{{- define "hivemq-platform.has-default-metrics-service" -}}
{{- $metricsServiceExists := "" }}
{{- $metricsPort := (include "hivemq-platform.metrics-port" .) }}
{{- range $service := .Values.services }}
  {{- if and (eq $service.type "metrics") (eq (int64 $service.containerPort) (int64 $metricsPort)) }}
    {{- $metricsServiceExists = true }}
    {{- break }}
  {{- end }}
{{- end }}
{{- $metricsServiceExists }}
{{- end -}}

{{/*
Checks if there is a `migration.statefulSet` value set to `true`..
Returns:
- `true` if a `.Values.migration.statufSet` flag is set to true, empty string otherwise.
Usage: {{ include "hivemq-platform.has-legacy-statefulset-migration" . }}
*/}}
{{- define "hivemq-platform.has-legacy-statefulset-migration" -}}
{{- $hasStatefulSetMigration := "" -}}
{{- if and (hasKey .Values "migration") (hasKey .Values.migration "statefulSet") (eq .Values.migration.statefulSet true) -}}
    {{- $hasStatefulSetMigration = true -}}
{{- end -}}
{{- $hasStatefulSetMigration -}}
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
Gets the metrics port value from the .Values.metrics value.
If metrics is disabled, the metrics port will be 0. Otherwise, it will use the `port` value defined for the metrics.
Usage: {{ include "hivemq-platform.metrics-port" . }}
*/}}
{{- define "hivemq-platform.metrics-port" -}}
{{- $metrics := .Values.metrics }}
{{- $metricsPort := 0 }}
{{- if $metrics.enabled }}
  {{- $metricsPort = $metrics.port }}
{{- end }}
{{- $metricsPort }}
{{- end -}}

{{/*
Gets the default cluster transport port value.
Usage: {{ include "hivemq-platform.cluster-transport-port" . }}
*/}}
{{- define "hivemq-platform.cluster-transport-port" -}}
{{- 7000 }}
{{- end -}}

{{/*
Validates all the exposed extensions.
- No duplicated extension names are defined as part of the `.Values.extensions` values list.
- No extension contains `configMapName` and `secretName` defined simultaneously.
Usage: {{ include "hivemq-platform.validate-extensions" . }}
*/}}
{{- define "hivemq-platform.validate-extensions" -}}
{{- include "hivemq-platform.validate-duplicated-extension-names" . -}}
{{- include "hivemq-platform.validate-configmap-secret-extensions" . -}}
{{- end -}}

{{/*
Validates there is no duplicated extension names defined.
Usage: {{ include "hivemq-platform.validate-duplicated-extension-names" . }}
*/}}
{{- define "hivemq-platform.validate-duplicated-extension-names" -}}
{{- $extensions := .Values.extensions }}
{{- $extensionNamesList := list }}
{{- range $extension := $extensions }}
  {{- if (has $extension.name $extensionNamesList) }}
    {{- fail (printf "\nFound duplicated extension name `%s`" $extension.name) }}
  {{- else }}
    {{- $extensionNamesList = $extension.name | append $extensionNamesList}}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates there is no enabled extension which defines both `configMapName` and `secretName` simultaneously.
Usage: {{ include "hivemq-platform.validate-configmap-secret-extensions" . }}
*/}}
{{- define "hivemq-platform.validate-configmap-secret-extensions" -}}
{{- $extensions := .Values.extensions }}
{{- range $extension := $extensions }}
  {{- if and $extension.configMapName $extension.secretName }}
    {{- fail (printf "\nBoth `configMapName` and `secretName` values are set for extension `%s`. Only one can be defined at a time" $extension.name) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates all the exposed services.
- No duplicated service names are defined as part of the `.Values.services` values list.
- No duplicated HiveMQ listener names are defined as part of the `.Values.services` values list.
- No default ports (7979, 8889, 7000) are defined as part of the `containerPort` defined in the `.Values.services` values list.
- When migration.statefulSet flag is enabled, `.Values.services.legacyPortName` is mandatory.
Usage: {{ include "hivemq-platform.validate-services" (dict "services" .Values.services "releaseName" $.Release.Name) }}
*/}}
{{- define "hivemq-platform.validate-services" -}}
{{- $services := .Values.services }}
{{- $releaseName := .Release.Name }}
{{- include "hivemq-platform.validate-duplicated-service-names" . -}}
{{- include "hivemq-platform.validate-duplicated-listener-names" . -}}
{{- include "hivemq-platform.validate-service-container-ports" . -}}
{{- include "hivemq-platform.validate-default-service-ports" . -}}
{{- include "hivemq-platform.validate-metrics-services" . -}}
{{- include "hivemq-platform.validate-hivemq-proxy-protocol-services" . -}}
{{- include "hivemq-platform.validate-hivemq-listener-name-services" . -}}
{{- include "hivemq-platform.validate-external-traffic-policy" . -}}
{{- include "hivemq-platform.validate-legacy-services" . -}}
{{- end -}}

{{/*
Validates there is no duplicated service name defined on exposed services.
Usage: {{ include "hivemq-platform.validate-duplicated-service-ports" . }}
*/}}
{{- define "hivemq-platform.validate-duplicated-service-names" -}}
{{- $services := .Values.services }}
{{- $releaseName := .Release.Name }}
{{- $serviceNamesDict := dict }}
{{- $serviceName := "" }}
{{- range $service := $services }}
  {{- $serviceName = (include "hivemq-platform.range-service-name" (dict "name" $service.name "releaseName" $releaseName "type" $service.type "port" $service.port "containerPort" $service.containerPort "legacyPortName" $service.legacyPortName)) }}
  {{- if (hasKey $serviceNamesDict $serviceName) }}
    {{- $matchingService := get $serviceNamesDict $serviceName }}
    {{- if not (eq $matchingService.exposed $service.exposed) }}
        {{- fail (printf "\nAmbiguous definition found for service %s (set as exposed and not exposed)" $serviceName) }}
    {{- else }}
        {{- fail (printf "\nFound duplicated service name %s" $serviceName) }}
    {{- end }}
  {{- else }}
    {{- $serviceNamesDict = set $serviceNamesDict $serviceName $service }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates there is no duplicated HiveMQ listener name defined on exposed services.
Usage: {{ include "hivemq-platform.validate-duplicated-listener-names" . }}
*/}}
{{- define "hivemq-platform.validate-duplicated-listener-names" -}}
{{- $services := .Values.services }}
{{- $listenerNamesList := list }}
{{- $listenerName := "" }}
{{- range $service := $services }}
  {{- $listenerName = $service.hivemqListenerName }}
  {{- if and $listenerName $service.exposed (has $listenerName $listenerNamesList) }}
    {{- fail (printf "\nFound duplicated HiveMQ listener name `%s`" $listenerName) }}
  {{- end }}
  {{- $listenerNamesList = $listenerName | append $listenerNamesList}}
{{- end }}
{{- end -}}

{{/*
Validates there is no duplicated container port for different service types.
Usage: {{ include "hivemq-platform.validate-service-container-ports" . }}
*/}}
{{- define "hivemq-platform.validate-service-container-ports" -}}
{{- $services := .Values.services }}
{{- $servicesDict := dict }}
{{- range $service := $services }}
  {{- $containerPort := $service.containerPort | toString }}
  {{- if (hasKey $servicesDict $containerPort) }}
    {{- $matchingServiceContainerPort := get $servicesDict $containerPort }}
    {{- if and ($service.exposed) (not (eq $matchingServiceContainerPort.type $service.type)) }}
        {{- fail (printf "\nServices with same container port (%s) but different types cannot be set" $containerPort) }}
    {{- end }}
  {{- else if $service.exposed }}
    {{- $servicesDict = set $servicesDict $containerPort $service }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates there is no default `containerPort` (7979, 8889, 7000) defined as part of the exposed services ports.
Usage: {{ include "hivemq-platform.validate-default-service-ports" . }}
*/}}
{{- define "hivemq-platform.validate-default-service-ports" -}}
{{- $services := .Values.services }}
{{- $defaultPortsList := list }}
{{- $defaultPortsList = ( include "hivemq-platform.operator-rest-api-port" . | int64 ) | append $defaultPortsList }}
{{- $defaultPortsList = ( include "hivemq-platform.health-api-port" . | int64 ) | append $defaultPortsList }}
{{- $defaultPortsList = ( include "hivemq-platform.cluster-transport-port" . | int64 ) | append $defaultPortsList }}
{{- range $service := $services }}
  {{- if and $service.exposed (has (int64 $service.containerPort) $defaultPortsList) }}
    {{- fail (printf "\nContainer port %d in service `%s` already exists as part of one of the predefined ports (%s)" (int64 $service.containerPort) $service.type (join ", " $defaultPortsList)) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates legacy services to make sure:
- No `legacyPortName` value is used when the `migration.statufulSet` value is disabled.
- No `port` value is used when the `migration.statefulSet` value is enabled.
Usage: {{ include "hivemq-platform.validate-duplicated-service-ports" . }}
*/}}
{{- define "hivemq-platform.validate-legacy-services" -}}
{{- $services := .Values.services }}
{{- $hasStatefulSetMigration := ( include "hivemq-platform.has-legacy-statefulset-migration" . ) }}
{{- range $service := $services }}
  {{- if and ($service.exposed) ($hasStatefulSetMigration) (hasKey $service "port") }}
    {{- fail (printf "\nService type `%s` with container port `%d` cannot use `port` value as `migration.statefulSet` value is enabled" $service.type (int64 $service.containerPort)) }}
  {{- end }}
  {{- if and ($service.exposed) (not $hasStatefulSetMigration) (hasKey $service "legacyPortName") }}
    {{- fail (printf "\nService type `%s` with container port `%d` cannot use `legacyPortName` value as `migration.statefulSet` value is disabled" $service.type (int64 $service.containerPort)) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates that the metrics services defined:
- Match their container port with the HiveMQ Prometheus extension port defined in the `metrics.port`.
Usage: {{ include "hivemq-platform.validate-metrics-services" . }}
*/}}
{{- define "hivemq-platform.validate-metrics-services" -}}
{{- $services := .Values.services }}
{{- $metricsPort := (include "hivemq-platform.metrics-port" .) }}
{{- range $service := $services }}
  {{- if and (eq $service.type "metrics") ($service.exposed) (not (eq (int64 $service.containerPort) (int64 $metricsPort))) }}
    {{- fail (printf "\nService type `metrics` with container port `%d` cannot be different than the metrics port `%d` defined for the HiveMQ Prometheus extension value as `metrics.port`" (int64 $service.containerPort) (int64 $metricsPort)) }}
    {{- break }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates that proxy protocol value is only used by either MQTT or WebSocket services and the service is exposed.
Usage: {{ include "hivemq-platform.validate-hivemq-proxy-protocol-services" . }}
*/}}
{{- define "hivemq-platform.validate-hivemq-proxy-protocol-services" -}}
{{- $services := .Values.services }}
{{- range $service := $services }}
  {{- if and ($service.exposed) (hasKey $service "hivemqProxyProtocol") (and (not (eq $service.type "mqtt")) (not (eq $service.type "websocket"))) }}
    {{- fail (printf "\nService type `%s` with container port `%d` is using `hivemqProxyProtocol` value. HiveMQ PROXY protocol is only supported by MQTT and WebSocket services" $service.type (int64 $service.containerPort)) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates that HiveMQ listener name value is only used by either MQTT or WebSocket services and the service is exposed.
Usage: {{ include "hivemq-platform.validate-hivemq-listener-name-services" . }}
*/}}
{{- define "hivemq-platform.validate-hivemq-listener-name-services" -}}
{{- $services := .Values.services }}
{{- range $service := $services }}
  {{- if and ($service.exposed) (hasKey $service "hivemqListenerName") (and (not (eq $service.type "mqtt")) (not (eq $service.type "websocket"))) }}
    {{- fail (printf "\nService type `%s` with container port `%d` is using `hivemqListenerName` value. HiveMQ listener names are only supported by MQTT and WebSocket services" $service.type (int64 $service.containerPort)) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates that external traffic policy value is only present when the service type value is set to either NodePort or LoadBalancer
and the service is exposed.
Usage: {{ include "hivemq-platform.validate-external-traffic-policy" . }}
*/}}
{{- define "hivemq-platform.validate-external-traffic-policy" -}}
{{- $services := .Values.services }}
{{- range $service := $services }}
  {{- if and ($service.exposed) (hasKey $service "externalTrafficPolicy") (not (hasKey $service "serviceType")) }}
    {{- fail (printf "\nService type `%s` with container port `%d` is using `externalTrafficPolicy` value but `serviceType` value is not defined. Service type value is mandatory when using external traffic policy" $service.type (int64 $service.containerPort)) }}
  {{- end }}
  {{- if and ($service.exposed) (hasKey $service "externalTrafficPolicy") (hasKey $service "serviceType") (and (not (eq $service.serviceType "NodePort")) (not (eq $service.serviceType "LoadBalancer")) )  }}
    {{- fail (printf "\nService type `%s` with container port `%d` is using `externalTrafficPolicy` value. External traffic policy is only supported by NodePort and LoadBalancer service types" $service.serviceType (int64 $service.containerPort)) }}
  {{- end }}
{{- end }}
{{- end -}}

{{/*
Validates `runAsNonRoot` and `runAsUser` has a valid combination for the PodSecurityContext or SecurityContext.
Params:
- securityContext: Either `.Values.nodes.podSecurityContext` or `.Values.nodes.containerSecurityContext` values.
Usage: {{- include "hivemq-platform.validate-run-as-user-security-context" .Values.nodes.podSecurityContext }}
*/}}
{{- define "hivemq-platform.validate-run-as-user-security-context" -}}
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
- securityContext: Either `.Values.nodes.podSecurityContext` or `.Values.nodes.containerSecurityContext` values.
- indentation: Number of spaces to use for the indentation.
Usage: {{ include "hivemq-platform.generate-run-as-security-context" (dict "securityContext" .Values.nodes.podSecurityContext "indentation" 12) }}
*/}}
{{- define "hivemq-platform.generate-run-as-security-context" -}}
{{- $securityContext := .securityContext -}}
{{- $indentation := .indentation -}}
{{- if hasKey $securityContext "runAsNonRoot" -}}
    {{- printf "runAsNonRoot: %v" $securityContext.runAsNonRoot | nindent $indentation -}}
    {{- if eq $securityContext.runAsNonRoot false -}}
        {{- printf "runAsUser: 0" | nindent $indentation -}}
    {{- else if hasKey $securityContext "runAsUser" -}}
        {{- printf "runAsUser: %v" $securityContext.runAsUser | nindent $indentation -}}
    {{- else -}}
        {{- printf "runAsUser: 10000" | nindent $indentation -}}
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
Validates the additionalVolumes values have a valid combination, duplicated volume mount are present and no duplicated volumes
with different type exist
Params:
- additionalVolumes: The `.Values.additionalVolumes` value.
Usage: {{- include "hivemq-platform.validate-additional-volumes" . }}
*/}}
{{- define "hivemq-platform.validate-additional-volumes" -}}
{{- $additionalVolumes := .Values.additionalVolumes }}
{{- $volumeMountList := list }}
{{- range $additionalVolume := $additionalVolumes }}
    {{- if not (hasKey $additionalVolume "type") }}
        {{- fail (printf "\n`type` value is mandatory for all of the `additionalVolumes` defined") }}
    {{- end -}}
    {{- if and (not (hasKey $additionalVolume "path")) (or (eq $additionalVolume.containerName "hivemq") (not (hasKey $additionalVolume "containerName"))) }}
        {{- fail (printf "\n`path` values is mandatory for all of the `additionalVolumes` defined for the `hivemq` container") }}
    {{- end -}}
    {{- if and (not (hasKey $additionalVolume "name")) (not (hasKey $additionalVolume "mountName")) }}
        {{- fail (printf "\nAt least one of `name` or `mountName` values must be defined") }}
    {{- end -}}
    {{- if and (or (eq $additionalVolume.type "configMap") (eq $additionalVolume.type "secret") (eq $additionalVolume.type "persistentVolumeClaim")) (not (hasKey $additionalVolume "name")) }}
        {{- fail (printf "\n`name` value is required for types \"configMap\", \"secret\" and \"persistentVolumeClaim\"") }}
    {{- end -}}

    {{- $volumeName := "" }}
    {{- $containerName := $additionalVolume.containerName | default "hivemq" }}
    {{- if $additionalVolume.mountName }}
        {{- $volumeName = $additionalVolume.mountName }}
    {{- else }}
        {{- $volumeName = $additionalVolume.name }}
    {{- end -}}

    {{/* Check for duplicates volume mounts within the same container */}}
    {{- $volumeMountKey := printf "%s-%s" $volumeName $containerName }}
    {{- if has $volumeMountKey $volumeMountList }}
        {{- fail (printf "\nVolumeMount `%s` is duplicated for container `%s`" $volumeName $containerName) }}
    {{- else }}
        {{- $volumeMountList = $volumeMountKey | append $volumeMountList}}
    {{- end }}

    {{/* Volumes can only be defined with same name and same type */}}
    {{- range $volume := $additionalVolumes }}
    {{- if and (or (eq $volume.mountName $volumeName) (eq $volume.name $volumeName)) (not (eq $volume.type $additionalVolume.type)) }}
        {{- fail (printf "\nVolume `%s` is defined more than once but with different types" $volumeName) }}
    {{- end }}
    {{- end -}}

{{- end -}}
{{- end -}}

{{/*
Validates the broker license, additional broker licenses, the enterprise extension licenses and the Data Hub license so:
 - When the `license.create` value is enabled, at least one license content is defined.
 - Only one of `data` or `overrideLicense` are set, and not both.
 - No duplicated "license.lic" Secret entries is found for additional broker licenses.
Usage: {{ include "hivemq-platform.validate-licenses" . }}
*/}}
{{- define "hivemq-platform.validate-licenses" -}}
{{- $licenseContentExists := "" -}}
{{- if .Values.license.create -}}
    {{- include "hivemq-platform.validate-license-content" (dict "licenses" .Values.license.additionalLicenses "licenseLabel" "Additional HiveMQ Broker") -}}
    {{- include "hivemq-platform.validate-license-content" (dict "licenses" .Values.license.extensions "licenseLabel" "HiveMQ Enterprise Extension") -}}
    {{- include "hivemq-platform.validate-license-content" (dict "licenses" .Values.license.dataHub "licenseLabel" "HiveMQ Data Hub") -}}
    {{- if and (not .Values.license.overrideLicense) (not .Values.license.data) (not .Values.license.additionalLicenses) (not .Values.license.extensions) (not .Values.license.dataHub) -}}
        {{- fail (printf "\nHiveMQ Platform license content cannot be empty") -}}
    {{- end -}}
    {{- if and .Values.license.data .Values.license.overrideLicense -}}
        {{- fail (printf "\nBoth `data` and `overrideLicense` values are set for the HiveMQ Broker license content. Please, use only one of them") -}}
    {{- end -}}
    {{- include "hivemq-platform.validate-duplicated-additional-broker-license" . -}}
{{- end -}}
{{- end -}}

{{/*
Validates the licenses content within a range function:
 - At least either `data` or `overrideLicense` is not empty.
 - Only one of `data` or `overrideLicense` are set, and not both.
Usage: {{ include "hivemq-platform.validate-license-content" (dict "licenses" .licenses "licenseLabel" "HiveMQ Enterprise Extension") }}
*/}}
{{- define "hivemq-platform.validate-license-content" -}}
{{- $label := .licenseLabel -}}
{{- range $licenseName, $license := .licenses -}}
    {{- if and (not (hasKey $license "data")) (not (hasKey $license "overrideLicense")) -}}
        {{- fail (printf "\nInvalid values for setting the %s '%s' license content. Only `data` or `overrideLicense` values are allowed" $label $licenseName) }}
    {{- else if and (not $license.data) (not $license.overrideLicense) -}}
        {{- fail (printf "\n%s '%s' license content cannot be empty. Please, use either `data` or `overrideLicense` values" $label $licenseName) }}
    {{- else if and $license.data $license.overrideLicense -}}
        {{- fail (printf "\nBoth `data` and `overrideLicense` values are set for the %s '%s' license content. Please, use only one of them" $label $licenseName) }}
    {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Validates there is no "license.lic" defined in the additional broker license values, so it doesn't clash with the default broker name.
Usage: {{ include "hivemq-platform.validate-duplicated-additional-broker-license" . }}
*/}}
{{- define "hivemq-platform.validate-duplicated-additional-broker-license" -}}
{{- if or .Values.license.data .Values.license.overrideLicense -}}
    {{- range $licenseName, $license := .Values.license.additionalLicenses -}}
      {{- if eq $licenseName "license" -}}
        {{- fail (printf "\nAdditional HiveMQ Broker license 'license' is already defined for the default broker license. Please, use a different license name") -}}
      {{- end -}}
    {{- end -}}
{{- end -}}
{{- end -}}

{{/*
Generates the Secret entry for each of the licenses defined in the license value list passed as argument.
Usage: {{ include "hivemq-platform.generate-licenses-content" (dict "licenses" .licenses "licenseExtension" ".lic") }}
*/}}
{{- define "hivemq-platform.generate-licenses-content" -}}
{{- $extension := .licenseExtension -}}
{{- range $licenseName, $license := .licenses -}}
    {{- if $license.data -}}
        {{- printf "%s%s: %s" $licenseName $extension $license.data | nindent 2 -}}
    {{- else if $license.overrideLicense -}}
        {{- printf "%s%s: |-" $licenseName $extension | nindent 2 -}}
        {{ range ($license.overrideLicense | b64enc) | toStrings -}}
            {{ . | nindent 4 -}}
        {{ end -}}
    {{- end -}}
{{- end -}}
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
Gets the additional `hivemq` container volume mounts
Usage: {{ include "hivemq-platform.get-additional-volume-mounts" . }}
*/}}
{{- define "hivemq-platform.get-additional-volume-mounts" -}}
{{- range $additionalVolume := .Values.additionalVolumes }}
{{/* need to filter out those additional volumes whose `containerName` is different than `hivemq` or where the `containerName` is not present (defaults to `hivemq`) */}}
{{- if or (not (hasKey $additionalVolume "containerName")) ( and (hasKey $additionalVolume "containerName") (eq $additionalVolume.containerName "hivemq")) }}
{{- if $additionalVolume.mountName }}
- name: {{ $additionalVolume.mountName }}
{{- else }}
- name: {{ $additionalVolume.name }}
{{- end}}
  {{- if $additionalVolume.subPath }}
  mountPath: {{ $additionalVolume.path }}/{{ $additionalVolume.subPath }}
  subPath: {{ $additionalVolume.subPath }}
  {{- else }}
  mountPath: {{ $additionalVolume.path }}
  {{- end }}
{{- end }}
{{- end -}}
{{- end -}}

{{/*
Checks if there is any HiveMQ Client Event History options set based on the `.Values.hivemqClientEventHistory` values
Params:
- hivemqClientEventHistory: The set of values from hivemqClientEventHistory
Returns:
- `true` if any of the expected values under the hivemqClientEventHistory top level root value is present, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-hivemq-client-event-history-config" }}
{{- $clientEventHistoryConfig := .hivemqClientEventHistory }}
{{- $contains := "" }}
{{- if or
    (hasKey $clientEventHistoryConfig "enabled")
    (hasKey $clientEventHistoryConfig "lifetime") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Checks if there is any HiveMQ Overload Protection options set based on the `.Values.hivemqOverloadProtection` values
Params:
- hivemqOverloadProtection: The set of values from hivemqOverloadProtection
Returns:
- `true` if any of the expected values under the hivemqOverloadProtection top level root value is present, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-overload-protection-config" }}
{{- $overloadProtectionConfig := .hivemqOverloadProtection }}
{{- $contains := "" }}
{{- if (hasKey $overloadProtectionConfig "enabled") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Checks if there is any HiveMQ Replication options set based on the `.Values.hivemqClusterReplication` values
Params:
- hivemqClusterReplication: The set of values from hivemqClusterReplication
Returns:
- `true` if any of the expected values under the hivemqClusterReplication top level root value is present, empty string otherwise.
*/}}
{{- define "hivemq-platform.has-cluster-replication-config" }}
{{- $replicationConfig := .hivemqClusterReplication }}
{{- $contains := "" }}
{{- if (hasKey $replicationConfig "replicaCount") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Checks if there is any HiveMQ restriction options set based on the `.Values.hivemqRestrictions` values
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
    (hasKey $restrictionsConfig "maxClientIdLength")
    (hasKey $restrictionsConfig "maxTopicLength") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Checks if there is any HiveMQ MQTT options set based on the `.Values.hivemqMqtt` values
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
Checks if there is any HiveMQ MQTT Add-on options set based on the `.Values.hivemqMqttAddons` values
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
Checks if there is any HiveMQ MQTT security options set based on the `.Values.hivemqMqttSecurity` values
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
    (hasKey $mqttSecurityConfig "controlCenterAuditLog")
    (hasKey $mqttSecurityConfig "restApiAuditLog") }}
{{- $contains = true }}
{{- end }}
{{- $contains }}
{{- end }}

{{/*
Gets the volumes references. Filters out duplicated volumes by name and type
Usage: {{ include "hivemq-platform.get-additional-volumes" . }}
*/}}
{{- define "hivemq-platform.get-additional-volumes" -}}
{{- $volumeList := list }}
{{- range $volume := .Values.additionalVolumes -}}
{{- $volumeName := "" }}
{{- $volumeType := $volume.type }}
{{- if $volume.mountName }}
    {{- $volumeName = $volume.mountName }}
{{- else }}
    {{- $volumeName = $volume.name }}
{{- end -}}
{{- $volumeKey := printf "%s-%s" $volumeName $volumeType }}
{{- if not (has $volumeKey $volumeList) }}
- name: {{ $volumeName }}
  {{- if eq $volumeType "configMap" }}
  configMap:
    name: {{ $volume.name }}
  {{- else if eq $volumeType "secret" }}
  secret:
    secretName: {{ $volume.name }}
  {{- else if eq $volumeType "emptyDir" }}
  emptyDir: {}
  {{- else if eq $volumeType "persistentVolumeClaim" }}
  persistentVolumeClaim:
    claimName: {{ $volume.name }}
  {{- end }}
{{- $volumeList = $volumeKey | append $volumeList}}
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


{{/*
Generates a HiveMQ config.xml content based on whether a ConfigMap or a Secret is used
Usage: {{ include "hivemq-platform.generate-hivemq-configuration" . }}
*/}}
{{- define "hivemq-platform.generate-hivemq-configuration" -}}
{{- $isSecret := (eq .Values.config.createAs "Secret") -}}
{{- if and .Values.config.overrideHiveMQConfig $isSecret -}}
    {{- range (.Values.config.overrideHiveMQConfig | b64enc) | toStrings }}
        {{- . | nindent 3 }}
    {{- end }}
{{- else if .Values.config.overrideHiveMQConfig }}
    {{- .Values.config.overrideHiveMQConfig | nindent 3 }}
{{- else }}
    {{- ternary (include "hivemq-platform.default-hivemq-configuration" . | b64enc | nindent 4) (include "hivemq-platform.default-hivemq-configuration" . | nindent 4) $isSecret }}
{{- end }}
{{- end -}}

{{/*
Generates a HiveMQ tracing.xml content based on whether a ConfigMap or a Secret is used
Usage: {{ include "hivemq-platform.generate-hivemq-tracing-configuration" . }}
*/}}
{{- define "hivemq-platform.generate-hivemq-tracing-configuration" -}}
{{- $isSecret := (eq .Values.config.createAs "Secret") -}}
{{- if and .Values.config.customTracingConfig $isSecret -}}
    {{- range (.Values.config.customTracingConfig | b64enc) | toStrings }}
        {{- . | nindent 4 }}
    {{- end }}
{{- else if .Values.config.customTracingConfig }}
    {{- .Values.config.customTracingConfig | nindent 4 }}
{{- else }}
    {{- ternary (include "hivemq-platform.default-hivemq-tracing-configuration" . | b64enc | nindent 4) (include "hivemq-platform.default-hivemq-tracing-configuration" . | nindent 4) $isSecret }}
{{- end }}
{{- end -}}

{{/*
Generates a HiveMQ custom logback.xml content based on whether a ConfigMap or a Secret is used
Usage: {{ include "hivemq-platform.generate-hivemq-logback-configuration" . }}
*/}}
{{- define "hivemq-platform.generate-hivemq-logback-configuration" -}}
{{- $isSecret := (eq .Values.config.createAs "Secret") -}}
{{- if and .Values.config.customLogbackConfig $isSecret -}}
    {{- range (.Values.config.customLogbackConfig | b64enc) | toStrings }}
        {{- . | nindent 4 }}
    {{- end }}
{{- else if .Values.config.customLogbackConfig }}
    {{- .Values.config.customLogbackConfig | nindent 4 }}
{{- end }}
{{- end -}}

{{/*
Validates that the required Prometheus Monitoring CRDs are installed in the Kubernetes cluster.
If the required CRDs are not present the ServiceMonitor cannot be installed and the installation fails.
Usage: {{- include "hivemq-platform.validate-prometheus-monitoring-stack-installed" . -}}
*/}}
{{- define "hivemq-platform.validate-prometheus-monitoring-stack-installed" -}}
{{- $isCRDPresent := .Capabilities.APIVersions.Has "monitoring.coreos.com/v1" }}
{{- if not $isCRDPresent }}
    {{- fail (printf "\nThere is no Prometheus ServiceMonitor CustomResourceDefinition (CRD) available in your Kubernetes cluster. Prometheus Monitoring CRDs are required before installing the ServiceMonitor resource.\nCheck out https://docs.hivemq.com/hivemq-platform-operator/observability.html#monitoring for more help and guidance.") }}
{{- end }}
{{- end }}

{{/*
Validates that the HiveMQ Prometheus extension is enabled. Fails, otherwise.
Usage: {{- include "hivemq-platform.validate-metrics-is-enabled" . -}}
*/}}
{{- define "hivemq-platform.validate-metrics-is-enabled" -}}
{{- if not .Values.metrics.enabled }}
    {{- fail (printf "\nHiveMQ Prometheus extension is disabled. Please, use `.metrics.enabled=true` to create a ServiceMonitor resource for the HiveMQ Platform.") }}
{{- end }}
{{- end }}

{{/*
Gets the default metrics port name based on the metrics port.
Usage: {{- include "hivemq-platform.metrics-port-name" . -}}
*/}}
{{- define "hivemq-platform.metrics-port-name" -}}
{{- printf "metrics-%s" ( include "hivemq-platform.metrics-port" . ) }}
{{- end }}

{{/*
Gets the metrics container port name based on the metrics port, or from the `legacyPortName` services value if
legacy StatefulSet migration is enabled.
Usage: {{- include "hivemq-platform.metrics-container-port-name" . -}}
*/}}
{{- define "hivemq-platform.metrics-container-port-name" -}}
{{- $metricsPortName := "" }}
{{- $metricsPort := ( include "hivemq-platform.metrics-port" . ) -}}
{{- $hasStatefulSetMigration := ( include "hivemq-platform.has-legacy-statefulset-migration" . ) -}}
{{- if not $hasStatefulSetMigration -}}
    {{- $metricsPortName = ( include "hivemq-platform.metrics-port-name" . ) }}
{{- else -}}
    {{- range $service := .Values.services }}
      {{- if and (eq $service.type "metrics") (eq (int64 $service.containerPort) (int64 $metricsPort)) }}
        {{- $metricsPortName = printf "%s" $service.legacyPortName }}
        {{- break }}
      {{- end }}
    {{- end }}
{{- end -}}
{{- $metricsPortName -}}
{{- end }}
