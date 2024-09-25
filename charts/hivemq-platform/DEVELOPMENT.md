## Development

Generate templates with custom values

```bash
helm template operator ./charts/hivemq-platform-operator -f my-operator-values.yaml
helm template platform ./charts/hivemq-platform -f my-platform-values.yaml
```

Simulate without deploying into the K8s cluster to check the correctness of the templates.

**NOTE**: CRD has to be installed first in the K8s cluster

```bash
kind create cluster
helm install operator ./charts/hivemq-platform-operator --dry-run
helm install platform ./charts/hivemq-platform --dry-run 
```

### Unit tests
To run unit tests, the Helm [helm-unittest](https://github.com/helm-unittest/helm-unittest?tab=readme-ov-file#helm-unittest) plugin is required to be installed as part of your Helm installation.
In order to run them all, just simply execute the following Gradle command:

```bash
./gradlew test
```

Or execute the Helm unittest command for the specific chart you want to test as below:
```bash
helm unittest ./charts/hivemq-platform-operator -f './tests/**/*_test.yaml'
helm unittest ./charts/hivemq-platform -f './tests/**/*_test.yaml'
```

### Integration tests

By default, the integration tests will build and load both HiveMQ Platform Operator and HiveMQ Platform Operator Init images from the checked out branch you are currently running the tests. The HiveMQ Platform image will be pulled from Docker Hub, unless specify otherwise.
In order to run them, just simply execute the following Gradle command:

```bash
./gradlew integrationTest
```
