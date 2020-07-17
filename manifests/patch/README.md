# HiveMQ custom resource patch files

You can use these files as a basis for your custom resource file structures. It contains patches for
* installing and configuring various extensions
* configuring licenses
* configuring port mappings and exposition

## Usage

To apply the kafka extension patch (Note: you must first create the kafka config map for this specific patch):

`kubectl patch hmqc <cluster-name> --type=merge --patch "$(cat kafka.yaml)"`.

See [Patch Kubernetes objects](https://kubernetes.io/docs/tasks/run-application/update-api-object-kubectl-patch/#use-a-strategic-merge-patch-to-update-a-deployment) for more information.