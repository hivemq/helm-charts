{{/*
Generates a default HiveMQ config.xml content
Usage: {{ include "hivemq-platform.default-hivemq-configuration" . }}
*/}}
{{- define "hivemq-platform.default-hivemq-configuration" -}}
<?xml version="1.0"?>
<hivemq xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="config.xsd">
  {{- $hastMqtt := include "hivemq-platform.has-service-type" (dict "services" .Values.services "expectedType" "mqtt") }}
  {{- $hasWebsocket := include "hivemq-platform.has-service-type" (dict "services" .Values.services "expectedType" "websocket") }}
  {{- if or $hastMqtt $hasWebsocket }}
  <listeners>
  {{- $containerPortsList := list }}
  {{- range $service := .Values.services }}
    {{- if and ($service.exposed) (not (has $service.containerPort $containerPortsList)) }}
    {{- if eq $service.type "mqtt" }}
    {{- if $service.keystoreSecretName }}
    <tls-tcp-listener>
      <port>{{ $service.containerPort }}</port>
      <bind-address>0.0.0.0</bind-address>
      {{- if $service.hivemqListenerName }}
      <name>{{ $service.hivemqListenerName }}</name>
      {{- end }}
      {{- if hasKey $service "hivemqProxyProtocol" }}
      <proxy-protocol>{{ $service.hivemqProxyProtocol }}</proxy-protocol>
      {{- end }}
      <tls>
        <keystore>
          <path>/tls-{{ $service.keystoreSecretName }}/{{ $service.keystoreSecretKey | default "keystore" }}</path>
          <password>{{ printf "${%s_%s_%s_%s}" $service.type $.Release.Name $service.keystoreSecretName "keystore_pass" }}</password>
          <private-key-password>{{ printf "${%s}" (include "hivemq-platform.keystore-private-password" (dict "releaseName" $.Release.Name "type" .type "keystoreSecretName" .keystoreSecretName "keystorePrivatePassword" .keystorePrivatePassword "keystorePrivatePasswordSecretKey" .keystorePrivatePasswordSecretKey)) }}</private-key-password>
        </keystore>
        {{- if $service.truststoreSecretName }}
        <truststore>
          <path>/tls-{{ $service.truststoreSecretName }}/{{ $service.truststoreSecretKey | default "truststore" }}</path>
          <password>{{ printf "${%s_%s_%s_%s}" $service.type $.Release.Name $service.truststoreSecretName "truststore_pass" }}</password>
        </truststore>
        {{- end }}
        {{- if $service.tlsClientAuthenticationMode}}
        <client-authentication-mode>{{ $service.tlsClientAuthenticationMode }}</client-authentication-mode>
        {{- end }}
      </tls>
    </tls-tcp-listener>
    {{- else }}
    <tcp-listener>
      <port>{{ $service.containerPort }}</port>
      <bind-address>0.0.0.0</bind-address>
      {{- if $service.hivemqListenerName }}
      <name>{{ $service.hivemqListenerName }}</name>
      {{- end }}
      {{- if hasKey $service "hivemqProxyProtocol" }}
      <proxy-protocol>{{ $service.hivemqProxyProtocol }}</proxy-protocol>
      {{- end }}
    </tcp-listener>
    {{- end }}
    {{- else if eq $service.type "websocket" }}
    {{- if $service.keystoreSecretName }}
    <tls-websocket-listener>
      <port>{{ $service.containerPort }}</port>
      <bind-address>0.0.0.0</bind-address>
      {{- if $service.hivemqListenerName }}
      <name>{{ $service.hivemqListenerName }}</name>
      {{- end }}
      {{- if hasKey $service "hivemqProxyProtocol" }}
      <proxy-protocol>{{ $service.hivemqProxyProtocol }}</proxy-protocol>
      {{- end }}
      <path>/mqtt</path>
      <tls>
        <keystore>
          <path>/tls-{{ $service.keystoreSecretName }}/{{ $service.keystoreSecretKey | default "keystore" }}</path>
          <password>{{ printf "${%s_%s_%s_%s}" $service.type $.Release.Name $service.keystoreSecretName "keystore_pass" }}</password>
          <private-key-password>{{ printf "${%s}" (include "hivemq-platform.keystore-private-password" (dict "releaseName" $.Release.Name "type" .type "keystoreSecretName" .keystoreSecretName "keystorePrivatePassword" .keystorePrivatePassword "keystorePrivatePasswordSecretKey" .keystorePrivatePasswordSecretKey)) }}</private-key-password>
        </keystore>
        {{- if $service.truststoreSecretName }}
        <truststore>
          <path>/tls-{{ $service.truststoreSecretName }}/{{ $service.truststoreSecretKey | default "truststore" }}</path>
          <password>{{ printf "${%s_%s_%s_%s}" $service.type $.Release.Name $service.truststoreSecretName "truststore_pass" }}</password>
        </truststore>
        {{- end }}
        {{- if $service.tlsClientAuthenticationMode}}
        <client-authentication-mode>{{ $service.tlsClientAuthenticationMode }}</client-authentication-mode>
        {{- end }}
      </tls>
    </tls-websocket-listener>
    {{- else }}
    <websocket-listener>
      <port>{{ $service.containerPort }}</port>
      <bind-address>0.0.0.0</bind-address>
      {{- if $service.hivemqListenerName }}
      <name>{{ $service.hivemqListenerName }}</name>
      {{- end }}
      {{- if hasKey $service "hivemqProxyProtocol" }}
      <proxy-protocol>{{ $service.hivemqProxyProtocol }}</proxy-protocol>
      {{- end }}
      <path>/mqtt</path>
    </websocket-listener>
    {{- end }}
    {{- end }}
    {{- end }}
    {{- if $service.exposed }}
      {{- $containerPortsList = $service.containerPort | append $containerPortsList}}
    {{- end }}
  {{- end }}
  </listeners>
  {{- end }}
  <cluster>
    <transport>
      <tcp>
        <bind-address>0.0.0.0</bind-address>
        <bind-port>{{- include "hivemq-platform.cluster-transport-port" . -}}</bind-port>
      </tcp>
    </transport>
    <enabled>true</enabled>
    <discovery>
      <extension/>
    </discovery>
    {{- $clusterReplicationConfig := .Values.hivemqClusterReplication }}
    {{- $hasclusterReplicationConfig := include "hivemq-platform.has-cluster-replication-config" (dict "hivemqClusterReplication" $clusterReplicationConfig) }}
    {{- if $hasclusterReplicationConfig }}
    <replication>
      {{- if (hasKey $clusterReplicationConfig "replicaCount") }}
      <replica-count>{{ $clusterReplicationConfig.replicaCount }}</replica-count>
      {{- end }}
    </replication>
    {{- end }}
  </cluster>
  <!-- required and should not be configured different -->
  <health-api>
    <enabled>true</enabled>
    <listeners>
      <http>
        <port>{{- include "hivemq-platform.health-api-port" . -}}</port>
        <bind-address>0.0.0.0</bind-address>
      </http>
    </listeners>
  </health-api>
  {{- $hasControlCenter := include "hivemq-platform.has-service-type" (dict "services" .Values.services "expectedType" "control-center") }}
  {{- if $hasControlCenter }}
  <control-center>
    <listeners>
    {{- $containerPortsList := list }}
    {{- range $service := .Values.services }}
      {{- if and ($service.exposed) (not (has $service.containerPort $containerPortsList)) }}
      {{- if eq $service.type "control-center" }}
      {{- if (hasKey $service "keystoreSecretName") }}
      <https>
        <port>{{ $service.containerPort }}</port>
        <bind-address>0.0.0.0</bind-address>
        <tls>
          <keystore>
            <path>/tls-{{ $service.keystoreSecretName }}/{{ $service.keystoreSecretKey | default "keystore" }}</path>
            <password>{{ printf "${%s_%s_%s_%s}" $service.type $.Release.Name $service.keystoreSecretName "keystore_pass" }}</password>
            <private-key-password>{{ printf "${%s}" (include "hivemq-platform.keystore-private-password" (dict "releaseName" $.Release.Name "type" .type "keystoreSecretName" .keystoreSecretName "keystorePrivatePassword" .keystorePrivatePassword "keystorePrivatePasswordSecretKey" .keystorePrivatePasswordSecretKey)) }}</private-key-password>
          </keystore>
        </tls>
      </https>
      {{- else }}
      <http>
        <port>{{ $service.containerPort }}</port>
        <bind-address>0.0.0.0</bind-address>
      </http>
      {{- end }}
      {{- end }}
      {{- end }}
      {{- if $service.exposed }}
        {{- $containerPortsList = $service.containerPort | append $containerPortsList}}
      {{- end }}
    {{- end }}
    </listeners>
    {{- if and .Values.controlCenter.username .Values.controlCenter.password }}
    <users>
      <user>
        <name>{{ .Values.controlCenter.username | trim }}</name>
        <password>{{ .Values.controlCenter.password | trim }}</password>
      </user>
    </users>
    {{- end }}
  </control-center>
  {{- end }}
  {{- $hasRestApi := include "hivemq-platform.has-service-type" (dict "services" .Values.services "expectedType" "rest-api") }}
  {{- if $hasRestApi }}
  <rest-api>
    <enabled>true</enabled>
    <auth>
      <enabled>{{ printf "%t" .Values.restApi.authEnabled | default false }}</enabled>
    </auth>
    {{- $containerPortsList := list }}
    {{- range $service := .Values.services }}
    {{- if and ($service.exposed) (not (has $service.containerPort $containerPortsList)) }}
    {{- if eq $service.type "rest-api" }}
    <listeners>
      <http>
        <port>{{ $service.containerPort }}</port>
        <bind-address>0.0.0.0</bind-address>
      </http>
    </listeners>
    {{- end }}
    {{- end }}
    {{- if $service.exposed }}
      {{- $containerPortsList = $service.containerPort | append $containerPortsList}}
    {{- end }}
    {{- end }}
  </rest-api>
  {{- end }}
  {{- if and .Values.config.dataHub (or .Values.config.dataHub.behaviorValidationEnabled .Values.config.dataHub.dataValidationEnabled) }}
  <data-hub>
    {{- if .Values.config.dataHub.dataValidationEnabled }}
    <data-validation>
      <enabled>true</enabled>
    </data-validation>
    {{- end }}
    {{- if .Values.config.dataHub.behaviorValidationEnabled }}
    <behavior-validation>
      <enabled>true</enabled>
    </behavior-validation>
    {{- end }}
  </data-hub>
  {{- end }}
  {{- $internalOptionsConfig := .Values.hivemqInternalOptions }}
  {{- if $internalOptionsConfig }}
  <internal>
    {{- range $internalOption := $internalOptionsConfig }}
    <option>
      <key>{{ $internalOption.key }}</key>
      <value>{{ $internalOption.value }}</value>
    </option>
    {{- end }}
  </internal>
  {{- end }}
  {{- $clientEventHistoryConfig := .Values.hivemqClientEventHistory }}
  {{- $hasclientEventHistoryConfig := include "hivemq-platform.has-hivemq-client-event-history-config" (dict "hivemqClientEventHistory" $clientEventHistoryConfig) }}
  {{- if $hasclientEventHistoryConfig }}
  <client-event-history>
    {{- if (hasKey $clientEventHistoryConfig "enabled") }}
    <enabled>{{ $clientEventHistoryConfig.enabled }}</enabled>
    {{- end -}}
    {{- if (hasKey $clientEventHistoryConfig "lifetime") }}
    <lifetime>{{ $clientEventHistoryConfig.lifetime }}</lifetime>
    {{- end }}
  </client-event-history>
  {{- end }}
  {{- $overloadProtectionConfig := .Values.hivemqOverloadProtection }}
  {{- $hasoverloadProtectionConfig := include "hivemq-platform.has-overload-protection-config" (dict "hivemqOverloadProtection" $overloadProtectionConfig) }}
  {{- if $hasoverloadProtectionConfig }}
  <overload-protection>
    {{- if (hasKey $overloadProtectionConfig "enabled") }}
    <enabled>{{ $overloadProtectionConfig.enabled }}</enabled>
    {{- end }}
  </overload-protection>
  {{- end }}
  {{- $restrictionsConfig := .Values.hivemqRestrictions }}
  {{- $hasRestrictionsConfig := include "hivemq-platform.has-hivemq-restrictions-config" (dict "hivemqRestrictions" $restrictionsConfig) }}
  {{- if $hasRestrictionsConfig }}
  <restrictions>
    {{- if (hasKey $restrictionsConfig "maxConnections") }}
    <max-connections>{{ $restrictionsConfig.maxConnections | int64 }}</max-connections>
    {{- end -}}
    {{- if (hasKey $restrictionsConfig "incomingBandwidthThrottling") }}
    <incoming-bandwidth-throttling>{{ $restrictionsConfig.incomingBandwidthThrottling | int64 }}</incoming-bandwidth-throttling>
    {{- end -}}
    {{- if (hasKey $restrictionsConfig "noConnectIdleTimeout") }}
    <no-connect-idle-timeout>{{ $restrictionsConfig.noConnectIdleTimeout | int64 }}</no-connect-idle-timeout>
    {{- end -}}
    {{- if (hasKey $restrictionsConfig "maxClientIdLength") }}
    <max-client-id-length>{{ $restrictionsConfig.maxClientIdLength }}</max-client-id-length>
    {{- end -}}
    {{- if (hasKey $restrictionsConfig "maxTopicLength") }}
    <max-topic-length>{{ $restrictionsConfig.maxTopicLength | int64 }}</max-topic-length>
    {{- end }}
  </restrictions>
  {{- end }}
  {{- $mqttConfig := .Values.hivemqMqtt }}
  {{- $hasMqttConfig := include "hivemq-platform.has-hivemq-mqtt-config" (dict "hivemqMqtt" $mqttConfig) }}
  {{- if $hasMqttConfig }}
  <mqtt>
    {{- if (hasKey $mqttConfig "sessionExpiryMaxInterval") }}
    <session-expiry>
      <max-interval>{{ $mqttConfig.sessionExpiryMaxInterval | int64 }}</max-interval>
    </session-expiry>
    {{- end -}}
    {{- if (hasKey $mqttConfig "messageExpiryMaxInterval") }}
    <message-expiry>
      <max-interval>{{ $mqttConfig.messageExpiryMaxInterval | int64 }}</max-interval>
    </message-expiry>
    {{- end -}}
    {{- if (hasKey $mqttConfig "maxPacketSize") }}
    <packets>
      <max-packet-size>{{ $mqttConfig.maxPacketSize | int64 }}</max-packet-size>
    </packets>
    {{- end -}}
    {{- if (hasKey $mqttConfig "serverReceiveMaximum") }}
    <receive-maximum>
      <server-receive-maximum>{{ $mqttConfig.serverReceiveMaximum }}</server-receive-maximum>
    </receive-maximum>
    {{- end -}}
    {{- if or (hasKey $mqttConfig "keepAliveMax") (hasKey $mqttConfig "keepAliveAllowUnlimited") }}
    <keep-alive>
      {{- if (hasKey $mqttConfig "keepAliveMax") }}
      <max-keep-alive>{{ $mqttConfig.keepAliveMax }}</max-keep-alive>
      {{- end -}}
      {{- if (hasKey $mqttConfig "keepAliveAllowUnlimited") }}
      <allow-unlimited>{{ printf "%t" $mqttConfig.keepAliveAllowUnlimited }}</allow-unlimited>
      {{- end }}
    </keep-alive>
    {{- end -}}
    {{- if or (hasKey $mqttConfig "topicAliasEnabled") (hasKey $mqttConfig "topicAliasMaxPerClient") }}
    <topic-alias>
      {{- if (hasKey $mqttConfig "topicAliasEnabled") }}
      <enabled>{{ printf "%t" $mqttConfig.topicAliasEnabled }}</enabled>
      {{- end -}}
      {{- if (hasKey $mqttConfig "topicAliasMaxPerClient") }}
      <max-per-client>{{ $mqttConfig.topicAliasMaxPerClient }}</max-per-client>
      {{- end }}
    </topic-alias>
    {{- end -}}
    {{- if (hasKey $mqttConfig "subscriptionIdentifier") }}
    <subscription-identifier>
      <enabled>{{ printf "%t" $mqttConfig.subscriptionIdentifier }}</enabled>
    </subscription-identifier>
    {{- end -}}
    {{- if (hasKey $mqttConfig "wildcardSubscriptions") }}
    <wildcard-subscriptions>
      <enabled>{{ printf "%t" $mqttConfig.wildcardSubscriptions }}</enabled>
    </wildcard-subscriptions>
    {{- end -}}
    {{- if (hasKey $mqttConfig "sharedSubscriptions") }}
    <shared-subscriptions>
      <enabled>{{ printf "%t" $mqttConfig.sharedSubscriptions }}</enabled>
    </shared-subscriptions>
    {{- end -}}
    {{- if (hasKey $mqttConfig "maxQualityOfService") }}
    <quality-of-service>
      <max-qos>{{ $mqttConfig.maxQualityOfService }}</max-qos>
    </quality-of-service>
    {{- end -}}
    {{- if (hasKey $mqttConfig "retainedMessages") }}
    <retained-messages>
      <enabled>{{ printf "%t" $mqttConfig.retainedMessages }}</enabled>
    </retained-messages>
    {{- end -}}
    {{- if or (hasKey $mqttConfig "queuedMessagesMaxSize") (hasKey $mqttConfig "queuedMessagesStrategy") }}
    <queued-messages>
      {{- if (hasKey $mqttConfig "queuedMessagesMaxSize") }}
      <max-queue-size>{{ $mqttConfig.queuedMessagesMaxSize | int64 }}</max-queue-size>
      {{- end -}}
      {{- if (hasKey $mqttConfig "queuedMessagesStrategy") }}
      <strategy>{{ $mqttConfig.queuedMessagesStrategy }}</strategy>
      {{- end }}
    </queued-messages>
    {{- end }}
  </mqtt>
  {{- end }}
  {{- $mqttAddonsConfig := .Values.hivemqMqttAddons }}
  {{- $hasMqttAddonsConfig := include "hivemq-platform.has-hivemq-mqtt-addons-config" (dict "hivemqMqttAddons" $mqttAddonsConfig) }}
  {{- if $hasMqttAddonsConfig }}
  <mqtt-addons>
    {{- if (hasKey $mqttAddonsConfig "expiredMessagesTopic") }}
    <expired-messages-topic>
      <enabled>{{ printf "%t" $mqttAddonsConfig.expiredMessagesTopic }}</enabled>
    </expired-messages-topic>
    {{- end -}}
    {{- if (hasKey $mqttAddonsConfig "droppedMessagesTopic") }}
    <dropped-messages-topic>
      <enabled>{{ printf "%t" $mqttAddonsConfig.droppedMessagesTopic }}</enabled>
    </dropped-messages-topic>
    {{- end -}}
    {{- if (hasKey $mqttAddonsConfig "deadMessagesTopic") }}
    <dead-messages-topic>
      <enabled>{{ printf "%t" $mqttAddonsConfig.deadMessagesTopic }}</enabled>
    </dead-messages-topic>
    {{- end }}
  </mqtt-addons>
  {{- end }}
  {{- $securityConfig := .Values.hivemqMqttSecurity }}
  {{- $hasMqttSecurityConfig := include "hivemq-platform.has-hivemq-mqtt-security-config" (dict "hivemqMqttSecurity" $securityConfig) }}
  {{- if $hasMqttSecurityConfig }}
  <security>
    {{- if (hasKey $securityConfig "allowEmptyClientId") }}
    <allow-empty-client-id>
      <enabled>{{ printf "%t" $securityConfig.allowEmptyClientId }}</enabled>
    </allow-empty-client-id>
    {{- end -}}
    {{- if (hasKey $securityConfig "payloadFormatValidation") }}
    <payload-format-validation>
      <enabled>{{ printf "%t" $securityConfig.payloadFormatValidation }}</enabled>
    </payload-format-validation>
    {{- end -}}
    {{- if (hasKey $securityConfig "utf8Validation") }}
    <utf8-validation>
      <enabled>{{ printf "%t" $securityConfig.utf8Validation }}</enabled>
    </utf8-validation>
    {{- end -}}
    {{- if (hasKey $securityConfig "allowRequestProblemInformation") }}
    <allow-request-problem-information>
      <enabled>{{ printf "%t" $securityConfig.allowRequestProblemInformation }}</enabled>
    </allow-request-problem-information>
    {{- end -}}
    {{- if (hasKey $securityConfig "controlCenterAuditLog") }}
    <control-center-audit-log>
      <enabled>{{ printf "%t" $securityConfig.controlCenterAuditLog }}</enabled>
    </control-center-audit-log>
    {{- end -}}
    {{- if (hasKey $securityConfig "restApiAuditLog") }}
    <rest-api-audit-log>
      <enabled>{{ printf "%t" $securityConfig.controlCenterAuditLog }}</enabled>
    </rest-api-audit-log>
    {{- end }}
  </security>
  {{- end }}
</hivemq>
{{- end -}}
