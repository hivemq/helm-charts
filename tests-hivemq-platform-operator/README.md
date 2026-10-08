# HiveMQ Platform Operator (new) Integration Tests

This submodule contains integration tests for validating the functionality and behavior of the HiveMQ Platform Operator (new) Helm Charts. The Kubernetes environment used extends the [K3S module](https://java.testcontainers.org/modules/k3s/) available within the Testcontainer framework.

## Overview

The integration tests in this submodule are designed to:

- Install a HiveMQ platform by using the HiveMQ Platform (new) chart.
- Verify the successful deployment and initialization of the HiveMQ Platform Operator (new).
- Validate the proper functioning of HiveMQ when including some HiveMQ extension within a Kubernetes cluster.
- Test a successful rolling restart of a HiveMQ Platform. 
- Test an installation using a custom configuration chart values.
- Verify after each installation of the HiveMQ platform, all HiveMQ components are working properly such as Rest API, WebSocket and MQTT listeners, DataHub, etc.
- Validate installation of the HiveMQ platform through non-root users.
- Test successfully upgrades of the HiveMQ platform with custom configuration values.
- Verify installation of the HiveMQ platform when using custom extension or transformations.

## Prerequisites

Before running the integration tests, make sure you meet the following prerequisites:

- Java 21+.
- Docker installed for building necessary Docker images.
- Gradle installed for running the tests.

## Running the Tests

1. Clone the repository:

   ```bash
   git clone https://github.com/hivemq/helm-charts.git
   cd helm-charts/tests-hivemq-platform-operator
   ```

2. Run the tests:

   ```bash
   ./gradlew integrationTest
   ```

**NOTE:** At the moment, these integration tests can only be executed on the [`HiveMQ Platform Operator Integration Tests`](../.github/workflows/hivemq-platform-operator-integration-test.yml) GitHub Actions Workflow. Cannot be executed locally.

## Testing custom HiveMQ Platform images

The official HiveMQ Platform image is built on Ubuntu with an Eclipse Temurin JRE. To catch problems that only appear on another operating system or Java runtime, a subset of these tests can run against a HiveMQ Platform image that is built for the test run.

**NOTE:** This is a smoke test of the HiveMQ Platform on different operating systems and Java runtimes. It shows that HiveMQ runs on a combination, nothing more, and is not a certification of it.

Set the `customPlatformImageVariant` property to build such an image and run the tests against it:

```bash
./gradlew integrationTest -PcustomPlatformImageVariant=temurin-21-ubi9
```

The image is built from the platform distribution of the `hivemq-platform` version in `gradle/libs.versions.toml` on top of the Java runtime base image of the variant. It is only served to the K3s cluster of the test run and is never published to a registry.

The following variants are available:

| Variant                   | Base image                                                                 | JVM     | Java   |
|---------------------------|----------------------------------------------------------------------------|---------|--------|
| `temurin-21-resolute`     | `eclipse-temurin:21-jre-resolute`                                          | HotSpot | 21     |
| `temurin-latest-resolute` | `eclipse-temurin:<latest>-jre-resolute`                                    | HotSpot | latest |
| `temurin-21-ubi9`         | `eclipse-temurin:21-jre-ubi9-minimal`                                      | HotSpot | 21     |
| `temurin-25-ubi10`        | `eclipse-temurin:25-jre-ubi10-minimal`                                     | HotSpot | 25     |
| `temurin-latest-ubi10`    | `eclipse-temurin:<latest>-jre-ubi10-minimal`                               | HotSpot | latest |
| `semeru-21-noble`         | `ibm-semeru-runtimes:open-21-jre-noble`                                    | OpenJ9  | 21     |
| `semeru-25-noble`         | `ibm-semeru-runtimes:open-25-jre-noble`                                    | OpenJ9  | 25     |
| `semeru-latest-noble`     | `ibm-semeru-runtimes:open-<latest>-jre-noble`                              | OpenJ9  | latest |
| `corretto-21-al2023`      | `amazoncorretto:21-al2023-jdk`                                             | HotSpot | 21     |
| `corretto-25-al2023`      | `amazoncorretto:25-al2023-jdk`                                             | HotSpot | 25     |
| `corretto-latest-al2023`  | `amazoncorretto:<latest>-al2023-jdk`                                       | HotSpot | latest |
| `oracle-25-ol9`           | `container-registry.oracle.com/java/jdk-no-fee-term:25-oraclelinux9`       | HotSpot | 25     |
| `oracle-latest-ol9`       | `container-registry.oracle.com/java/jdk-no-fee-term:<latest>-oraclelinux9` | HotSpot | latest |
| `redhat-21-ubi9`          | `registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24`                  | HotSpot | 21     |
| `redhat-25-ubi10`         | `registry.access.redhat.com/ubi10/openjdk-25-runtime:1.24`                 | HotSpot | 25     |

Only the tests tagged with `custom-platform-image` run in this mode, and a `--tests` filter can narrow the selection further:

```bash
./gradlew integrationTest -PcustomPlatformImageVariant=semeru-25-noble --tests '*HelmMqttIT*'
```

Without the property, the build resolves the official HiveMQ Platform image and runs the whole suite, as it does for every regular test run.

The [`HiveMQ Platform Custom Image Tests`](../.github/workflows/custom-platform-image-tests.yml) GitHub Actions Workflow runs every variant once a week, and can be started manually for a single variant.

To add a variant, add its Java runtime base image to [`gradle/oci.versions.toml`](gradle/oci.versions.toml) and the matching entry to the `jreBaseImages` map in [`build.gradle.kts`](build.gradle.kts). The workflow matrix is generated from that map.
