# HiveMQ Edge Integration Tests

This submodule contains integration tests for validating the functionality and behavior of the HiveMQ Edge Helm Chart. The Kubernetes environment used extends the [K3S module](https://java.testcontainers.org/modules/k3s/) available within the Testcontainer framework.

## Overview

The integration tests in this submodule are designed to:

- Install the HiveMQ Edge chart and verify the deployed pod starts up successfully.
- Validate that the deployed Edge image matches the expected version declared in `gradle/libs.versions.toml`.
- Run the chart's built-in `helm test` hook to verify MQTT connectivity through the `mqtt-cli` test pod.
- Verify a successful MQTT publish/subscribe round-trip against the deployed Edge instance.
- Verify the HiveMQ Edge REST API by authenticating and querying the available protocol adapter types.

## Prerequisites

Before running the integration tests, make sure you meet the following prerequisites:

- Java 25+.
- Docker installed for building necessary Docker images.
- Gradle installed for running the tests.

## Running the Tests

1. Clone the repository:

   ```bash
   git clone https://github.com/hivemq/helm-charts.git
   cd helm-charts/tests-hivemq-edge
   ```

2. Run the tests:

   ```bash
   ./gradlew integrationTest
   ```

**NOTE:** At the moment, these integration tests can only be executed on the [`HiveMQ Edge Helm Chart Integration Tests`](../.github/workflows/edge-integration-tests.yml) GitHub Actions Workflow. Cannot be executed locally.
