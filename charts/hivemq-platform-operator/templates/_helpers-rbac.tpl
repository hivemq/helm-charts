{{/*
Checks if cluster scoped RBAC rules should be created.
Usage: {{- include "hivemq-platform-operator.rbac.hasClusterRules" . }}
*/}}
{{- define "hivemq-platform-operator.rbac.hasClusterRules" -}}
{{- if or .Values.crd.apply .Values.crd.waitReady .Values.hivemqPlatformServiceAccount.permissions.validate (not .Values.namespaces) }}
  "true"
{{- end }}
{{- end -}}

{{/*
Creates the cluster scoped RBAC rules.
Params:
- crdApply: Defines if the rules to read and write CRDs are added.
- crdWaitReady: Defines if the rules to read CRDs are added.
- platformServiceAccountPermissionsValidate: Defines if the rules to validate the HiveMQ Platform ServiceAccount permissions are added.
Usage: {{- include "hivemq-platform-operator.rbac.rules.cluster" (dict "crdApply" .Values.crd.apply "crdWaitReady" .Values.crd.waitReady "platformServiceAccountPermissionsValidate" .Values.hivemqPlatformServiceAccount.permissions.validate) }}
*/}}
{{- define "hivemq-platform-operator.rbac.rules.cluster" -}}
{{- $crdApply := .crdApply }}
{{- $crdWaitReady := .crdWaitReady }}
{{- $platformServiceAccountPermissionsValidate := .platformServiceAccountPermissionsValidate }}
  {{- if or $crdApply $crdWaitReady }}
  - apiGroups:
      - apiextensions.k8s.io
    resources:
      - customresourcedefinitions
    verbs:
      - get
      - list
      - watch
      {{- if $crdApply }}
      - create
      - patch
      - update
      {{- end }}
  {{- end }}
  {{- if $platformServiceAccountPermissionsValidate }}
  - apiGroups:
      - rbac.authorization.k8s.io
    resources:
      - clusterroles
      - clusterrolebindings
    verbs:
      - get
      - list
      - watch
  {{- end }}
{{- end -}}

{{/*
Creates the namespace scoped RBAC rules.
Params:
- platformServiceAccountCreate:             Defines if the rules to create the HiveMQ Platform ServiceAccount are added.
- platformServiceAccountValidate:           Defines if the rules to validate the HiveMQ Platform ServiceAccount are added.
- platformServiceAccountPermissionsCreate:   Defines if the rules to create the HiveMQ Platform ServiceAccount permissions are added.
- platformServiceAccountPermissionsValidate: Defines if the rules to validate the HiveMQ Platform ServiceAccount permissions are added.
Usage: {{- include "hivemq-platform-operator.rbac.rules.namespace" (dict "platformServiceAccountCreate" .Values.hivemqPlatformServiceAccount.create "platformServiceAccountValidate" .Values.hivemqPlatformServiceAccount.validate "platformServiceAccountPermissionsCreate" .Values.hivemqPlatformServiceAccount.permissions.create "platformServiceAccountPermissionsValidate" .Values.hivemqPlatformServiceAccount.permissions.validate) }}
*/}}
{{- define "hivemq-platform-operator.rbac.rules.namespace" -}}
{{- $platformServiceAccountCreate := .platformServiceAccountCreate }}
{{- $platformServiceAccountValidate := .platformServiceAccountValidate }}
{{- $platformServiceAccountPermissionsCreate := .platformServiceAccountPermissionsCreate }}
{{- $platformServiceAccountPermissionsValidate := .platformServiceAccountPermissionsValidate }}
  - apiGroups:
      - hivemq.com
    resources:
      - hivemq-platforms
      - hivemq-platforms/status
      - hivemq-platforms/finalizers
    verbs:
      - get
      - list
      - watch
      - patch
      - update
      - create
      - delete
  - apiGroups:
      - ""
    resources:
      - configmaps
      - events
      - pods
      - secrets
      - services
    verbs:
      - get
      - list
      - watch
      - create
      - patch
      - update
      - delete
  - apiGroups:
      - apps
    resources:
      - statefulsets
    verbs:
      - get
      - list
      - watch
      - create
      - patch
      - update
      - delete
  - apiGroups:
      - ""
    resources:
      - pods/exec
    verbs:
      - create
      - watch
      - get
  {{- if or $platformServiceAccountCreate $platformServiceAccountValidate }}
  - apiGroups:
      - ""
    resources:
      - serviceaccounts
    verbs:
      - get
      - list
      - watch
      {{- if $platformServiceAccountCreate }}
      - create
      - patch
      - update
      - delete
      {{- end }}
  {{- end }}
  {{- if or $platformServiceAccountPermissionsCreate $platformServiceAccountPermissionsValidate }}
  - apiGroups:
      - rbac.authorization.k8s.io
    resources:
      - roles
      - rolebindings
    verbs:
      - get
      - list
      - watch
      {{- if $platformServiceAccountPermissionsCreate }}
      - create
      - patch
      - update
      - delete
      {{- end }}
  {{- end }}
{{- end -}}
