
```bash
cd charts/hivemq-edge/test/localdhtesting
```

```bash
mkdir /tmp/volume
kind delete cluster
kind create cluster --config  charts/hivemq-edge/test/localdhtesting/kind-cluster.yaml
kubectl apply -f charts/hivemq-edge/test/localdhtesting/setup.yaml
kind load docker-image hivemq/hivemq-edge:2025.5-SNAPSHOT
```

from the repo-root
```bash
helm install edge ./charts/hivemq-edge --set image.tag=2025.5-SNAPSHOT --values=charts/hivemq-edge/test/localdhtesting/values.yaml --set-file modules.dataHub.init=charts/hivemq-edge/test/localdhtesting/dh.json --set-file license.file=<PATH_TO_LICENSE> 
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
