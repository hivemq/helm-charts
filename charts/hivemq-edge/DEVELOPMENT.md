## Development

Generate templates with custom values

```bash
helm template edge ./charts/hivemq-edge -f my-edge-values.yaml
helm upgrade edge ./charts/hivemq-edge -f my-edge-values.yaml
```

Simulate without deploying into the K8s cluster to check the correctness of the templates.


```bash
helm install edge ./charts/hivemq-edge 
```

### Local testing KIND

First install Kind (Kubernetes in Docker), see their [userguid](https://kind.sigs.k8s.io/docs/user/quick-start#installation).

Install cloud-provider-kind to get a load balancer in Kind, follow the [docs](https://kind.sigs.k8s.io/docs/user/loadbalancer).

Fastest way is going through go directly (don't forget to then open a new shell to actually use it)

```
go install sigs.k8s.io/cloud-provider-kind@latest
```

Apply Helm:

```bash
kind create cluster
kubectl cluster-info --context kind-kind
helm install edge ./charts/hivemq-edge 
```

Start the cloud provider to enable load balancing:

```bash
sudo cloud-provider-kind
```

**Note:**
The cloud-provider-kind needs to be restarted after deleting and recreating things. Not sure why that is.

Create a file `service.yaml` with the following content:

```yaml
kind: Service
apiVersion: v1
metadata:
  name: hivemq-edge-lb
spec:
  type: LoadBalancer
  selector:
    app.kubernetes.io/name: "hivemq-edge"
    app.kubernetes.io/instance: "edge"
  ports:
  - port: 5678
    targetPort: 8080
    name: http
  - port: 5679
    targetPort: 1883
    name: mqtt
  - port: 5680
    targetPort: 8883
    name: mqtts
```

```
kubectl apply -f service.yaml
```

Get the local ip address:
```bash
kubectl get svc/hivemq-edge-lb -o=jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

### Testing with local images

Checkout the hivemq-edge repository, cd to hivemq-edge/hivemq-edge and run `docker/build.sh`.
After this you now have an image named `hivemq/hivemq-edge:snapshot` in your local Docker environment.

Load the image into Kind using this `kind load docker-image hivemq/hivemq-edge:snapshot`.

**IMPORTANT:**

Make sure that in the StatefulSet you have `imagePullPolicy: Never`.

This ensures Kind will only use the images it already has and not try to pull a new version from somewhere else.

When running helm you have to override the tag to use the snapshot build;

```
helm ... --set image.tag=snapshot
```

### Provide adapter configs

We recommend setting the actual config via set-file.

Create the config like the fragment.xml (see layout in files) and add what's needed:

```
helm template edge ./charts/hivemq-edge -f values.yaml --set-file config=fragment.xml
```

### Testing commercial features

Download the licenses as a file, e.g. **license.edgelic**.

If the file is too big use set-file:
```bash
helm template edge ./charts/hivemq-edge -f values.yaml --set-file license.file=license.edgelic_b64
```

### Testing mqtts

Use [genkeystore.sh](test%2Fgenkeystore.sh) to generate a keystore with a self signed cert.
Encode it to base64.

```bash
base64 -i keystore.jks | tr -d '\n' >  keystore.jks_b64
```

In your values.yaml add:
```yaml
mqtts:
  enabled: true
  create:
    enabled: true
    file: <BASE64 ENCODED FILE CONTENT>
```

Since the file is huge I recommend setting via set-file:
```
helm --debug template edge ./charts/hivemq-edge -f values.yaml --set-file mqtts.create.file=keystore.jks_b64
```

### Testing mqtts clientauth

Encode the truststore to base64.

```bash
base64 -i truststore.jks | tr -d '\n' > truststore.jks_b64
```

In your values.yaml add:
```yaml
mqttsClientauth:
  clientAuthenticationMode: REQUIRED
  create:
    enabled: true
    file: <BASE64 ENCODED CONTENT>
```

Since the file is huge I recommend setting via set-file:
```
helm --debug template edge ./charts/hivemq-edge -f values.yaml --set-file mqttsClientauth.create.file=keystore.jks_b64
```

### Adding an adapter
Edit the values.yaml to contain:

```yaml
config: |
  <protocol-adapters>
      <protocol-adapter>
          <adapterId>simulation</adapterId>
          <protocolId>simulation</protocolId>
          <config>
              <simulationToMqtt>
                  <pollingIntervalMillis>100</pollingIntervalMillis>
                  <maxPollingErrorsBeforeRemoval>-1</maxPollingErrorsBeforeRemoval>
              </simulationToMqtt>
          </config>
          <northboundMappings>
              <northboundMapping>
                  <topic>test</topic>
                  <tagName>t2</tagName>
                  <mqttUserProperties>
                      <mqttUserProperty>
                          <name>simulation</name>
                          <value>2</value>
                      </mqttUserProperty>
                  </mqttUserProperties>
              </northboundMapping>
          </northboundMappings>
      </protocol-adapter>
  </protocol-adapters>
```

After applying the config it will take a little and the new adapter will be available in the UI.


### Unit tests

To run unit tests, the Helm [helm-unittest](https://github.com/helm-unittest/helm-unittest?tab=readme-ov-file#helm-unittest) plugin is required to be installed as part of your Helm installation.
In order to run them all, just simply execute the following Gradle command:

```bash
./gradlew test
```

Or execute the Helm unittest command for the specific chart you want to test as below:

```bash
helm unittest ./charts/hivemq-edge -f './tests/**/*_test.yaml'
```

### Integration tests

By default, the integration tests will build and load HiveMQ Edge image from the checked out branch you are currently running the tests.
In order to run them, just simply execute the following Gradle command:

```bash
./gradlew integrationTest
```
