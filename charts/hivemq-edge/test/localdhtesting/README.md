
```bash
cd charts/hivemq-edge/test/localdhtesting
```

```bash
mkdir /tmp/volume
kind delete cluster
kind create cluster --config  kind-cluster.yaml
kubectl apply -f setup.yaml
kind load docker-image hivemq/hivemq-edge:2025.5-SNAPSHOT
```

from the repo-root
```bash
helm install edge ./charts/hivemq-edge -f values.yaml --set-file license.file=<LICENSE>
```


./gradlew loadOciImage
|
docker - container-registry (127.0.0.1)
|              |
|     kind load docker-image hivemq/hivemq-edge:2025.4-SNAPSHOT
|              |
KIND             |
|              |
container-registry (172.12.10.1)
