# HiveMQ Operator (legacy) Integration Tests

This submodule contains integration tests for validating the functionality and behavior of the HiveMQ Operator (legacy) via Helm Charts in a Kubernetes environment. This Kubernetes environment is mimic by extending the [K3S module](https://java.testcontainers.org/modules/k3s/) available within the Testcontainer framework.

## Overview

The integration tests in this submodule are designed to:

- Install a HiveMQ cluster by using the HiveMQ Operator (legacy) chart.
- Verify the successful deployment and initialization of the HiveMQ Operator (legacy).
- Verify compatibility of the charts with different Kubernetes version.
- Validate the proper functioning of HiveMQ when including some HiveMQ extension within a Kubernetes cluster.
- Test an installation using a custom non-root container image.

## Prerequisites

Before running the integration tests, make sure you meet the following prerequisites:

- Java 21+.
- Docker installed for building necessary Docker images.
- Gradle installed for running the tests.

## Running the Tests

1. Clone the repository:

   ```bash
   git clone https://github.com/hivemq/helm-charts.git
   cd helm-charts/tests-hivemq-operator
   ```
   
2. Run the tests:

   ```bash
   ./gradlew integrationTest
   ```
   
**NOTE:** At the moment, these integration tests can only be executed on the [`HiveMQ Operator (Legacy) Integration Tests`](../.github/workflows/hivemq-operator-integration-test.yml) GitHub Actions Workflow. Cannot be executed locally.
