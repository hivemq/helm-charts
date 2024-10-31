# HiveMQ operator (legacy)

See the [documentation](https://docs.hivemq.com/hivemq-operator/) for detailed getting started and configuration guidance.

NOTE: `The HiveMQ Operator (legacy) will be sunsetted and cease to receive updates or further support 6 months after the next LTS release of the HiveMQ Platform. The HiveMQ Platform Operator (new) offers usability, stability, and performance enhancements, and will be the supported version for all future LTS releases.`

This chart provisions the

- HiveMQCluster CustomResourceDefinition
- HiveMQ operator as Deployment
- a configurable HiveMQCluster custom resource representing a cluster
- (optionally) Prometheus-operator integration
  - prometheus-operator sub-chart if you want to deploy a dedicated Prometheus instance
  - Grafana dashboard deployed along with the sub-chart

## Installing on Kubernetes clusters <1.16

Since chart version 0.9.0, we are shipping a v1 CustomResourceDefinition by default in order to be compatible with future Kubernetes versions.

If you are deploying the HiveMQ operator on a Kubernetes cluster that does not support the v1 CustomResourceDefinition API, you can use the following steps to deploy the chart:

The v1beta1 CRD still supports all the same fields as the v1 CRD.

```sh
# 1. Deploy the v1beta1 CRD manually
kubectl apply -f https://raw.githubusercontent.com/hivemq/helm-charts/master/manifests/hivemq-operator/legacy/v1beta1-hivemq-cluster.yaml
# 2. Deploy the Helm chart, skip CRD deployment
helm install hivemq-operator hivemq/hivemq-operator --skip-crds
```

## Verify the chart

```console
curl -L -o public.pgp https://www.hivemq.com/public.pgp
gpg --import public.pgp
gpg --export >~/.gnupg/pubring.gpg
curl -L -o hivemq-operator-x.y.z.prov https://github.com/hivemq/helm-charts/releases/download/hivemq-operator-x.y.z/hivemq-operator-x.y.z.tgz.prov
curl -L -o hivemq-operator-x.y.z.tgz https://github.com/hivemq/helm-charts/releases/download/hivemq-operator-x.y.z/hivemq-operator-x.y.z.tgz
helm verify hivemq-operator-x.y.z.tgz
```

**NOTE** Helm does not support GPG version 2 or higher so you have to convert your GPG keyring to the legacy GPG format:
```shell
gpg --export >~/.gnupg/pubring.gpg
```
_See the HiveMQ [documentation](https://docs.hivemq.com/hivemq-operator/) for more details._
