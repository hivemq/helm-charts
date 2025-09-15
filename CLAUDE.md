# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository contains HiveMQ Helm charts for deploying HiveMQ MQTT broker components in Kubernetes. The project consists of 5 main Helm charts:

- **hivemq-platform-operator** (new): Deploys the HiveMQ Platform Operator that manages HiveMQ Platform clusters
- **hivemq-platform** (new): Specifies HiveMQ Platform deployment that the Platform Operator installs
- **hivemq-operator** (legacy): Deploys the legacy HiveMQ Operator and HiveMQ cluster
- **hivemq-swarm**: Deploys HiveMQ Swarm for IoT testing and simulation
- **hivemq-edge**: Deploys HiveMQ Edge for IoT edge computing

## Build System

The project uses Gradle with Kotlin DSL for build management and version updates.

### Common Commands

```bash
# Run all Helm unit tests
./gradlew test
# Alternative: ./scripts/helm-unittest.sh

# Update chart versions (individual charts)
./gradlew updatePlatformChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z
./gradlew updatePlatformOperatorChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z
./gradlew updateOperatorChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z
./gradlew updateSwarmChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z
./gradlew updateEdgeChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z

# Update all platform charts at once (excludes Platform Operator and Edge)
./gradlew updateAllPlatformChartVersions -PappVersion=x.y.z

# Update all manifest files
./gradlew updateAllManifestFiles
```

### Version Update Architecture

The Gradle build system includes sophisticated version update tasks that:
- Auto-bump chart versions if not specified (increments patch version)
- Update both Chart.yaml and values.yaml files simultaneously
- Use regex patterns to update Docker image tags in values files
- Generate manifests in the `/manifests` folder after version updates
- Handle different quoting requirements for different charts (Edge charts quote app versions)

## Testing

### Helm Unit Tests
- Tests are located in each chart's `tests/` directory with `*_test.yaml` naming convention
- Requires `helm unittest` plugin to be installed
- Tests all 5 charts: hivemq-edge, hivemq-operator, hivemq-platform, hivemq-platform-operator, hivemq-swarm

### Integration Tests
Two separate Gradle submodules for integration testing:
- `tests-hivemq-platform-operator/`: Tests for the new Platform Operator (Java 21, Testcontainers, K3s)
- `tests-hivemq-operator/`: Tests for the legacy Operator

Integration tests use:
- Java 21 toolchain
- JUnit Jupiter 
- Testcontainers with K3s for Kubernetes testing
- Docker images built through OCI Gradle plugin
- Custom HiveMQ extensions testing

### Running Integration Tests
```bash
# Platform Operator integration tests
cd tests-hivemq-platform-operator
./gradlew integrationTest

# Legacy Operator integration tests  
cd tests-hivemq-operator
./gradlew integrationTest
```

## Chart Security

Charts are PGP-signed during packaging with `.prov` files generated alongside packaged charts. Public key available at https://www.hivemq.com/public.pgp.

## Repository Structure

- `/charts/`: Contains the 5 main Helm charts
- `/manifests/`: Rendered YAML files from charts with default values
- `/examples/`: Configuration examples and platform-specific files (e.g., OpenShift)
- `/scripts/`: Build and test automation scripts
- `/.github/workflows/`: Extensive CI/CD pipeline with chart verification, smoke tests, and integration tests
- `/tests-hivemq-platform-operator/` and `/tests-hivemq-operator/`: Integration test suites

## Development Workflow

1. Every PR to master requires a chart version bump to indicate template changes
2. Chart versions follow semantic versioning
3. Manifests are auto-generated from charts and should not be manually edited
4. Use the provided Gradle tasks for version updates to maintain consistency across Chart.yaml, values.yaml, and dependent test files