## Development

Generate templates with values to check

```bash
helm template ./hivemq-platform-operator
helm template ./hivemq-platform
```

Simulate without deploying into the k8s cluster to check the correctness of the templates.

NOTE: CRD has to be installed first in the k8s cluster

```bash
kind create cluster
helm install --dry-run my-operator-release hivemq-platform-operator
helm install --dry-run my-platform-release hivemq-platform 
```

### Integration tests

By default, the integration tests will build and load both HiveMQ Platform Operator and HiveMQ Platform Operator Init images from the checked out branch you are currently running the tests. The HiveMQ Platform image will be pulled from Docker Hub, unless specify otherwise.
In order to run them, just simply execute the following Gradle command:

```bash
./gradlew integrationTest
```
