
```bash
mkdir /tmp/volume #create the folder which will contain the persistent volume data
kind delete cluster #delete any existing kind cluster
kind create cluster --config  charts/hivemq-edge/test/ldaprolestesting/kind-cluster.yaml #create a new cluster with a volume manager
kubectl apply -f charts/hivemq-edge/test/ldaprolestesting/setup.yaml
kind load docker-image hivemq/hivemq-edge:2026.5-SNAPSHOT #load image from local registry into kind
```

## LDAP testing

```bash
helm install edge ./charts/hivemq-edge --set image.tag=2026.5-SNAPSHOT --values=charts/hivemq-edge/test/ldaprolestesting/values.yaml
```
## Interact with UI

```bash
sudo cloud-provider-kind
```
