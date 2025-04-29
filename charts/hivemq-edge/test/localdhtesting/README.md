
```bash
mkdir /tmp/volume #create the folder which will contain the persistent volume data
kind delete cluster #delete any existing kind cluster
kind create cluster --config  charts/hivemq-edge/test/localdhtesting/kind-cluster.yaml #create a new cluster with a volume manager
kubectl apply -f charts/hivemq-edge/test/localdhtesting/setup.yaml #create the volume manager
kind load docker-image hivemq/hivemq-edge:2025.7-SNAPSHOT #load image from local registry intp kind
```

The next command brings up edge with the default config loaded.
Make sure to have a license locally available if you want to test commercial features.
```bash
helm install edge ./charts/hivemq-edge --set image.tag=2025.7-SNAPSHOT --values=charts/hivemq-edge/test/localdhtesting/values.yaml --set-file modules.dataHub.init=charts/hivemq-edge/test/localdhtesting/dh.json --set-file license.file=<PATH_TO_LICENSE> 
```

To get a local build of edge execute the following  command in the hivemq-edge repo.
```
./gradlew loadOciImage
```

|
docker - container-registry (127.0.0.1)
|              |
|     kind load docker-image hivemq/hivemq-edge:2025.4-SNAPSHOT
|              |
KIND           |
|              |
container-registry (172.12.10.1)
