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
