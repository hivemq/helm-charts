# Helm test image

Builds and publishes `ghcr.io/hivemq/helm-test-image`, a container image containing
nothing but the Helm binary.

## Disclaimer

This image exists solely to provide Helm to the integration tests in this repository and in the
HiveMQ Kubernetes test suite. It is not a HiveMQ product, it is not supported, and it carries no
compatibility guarantees. Its contents, tags, and existence may change at any time without notice.
Do not depend on it.

Helm itself is unmodified and is downloaded from [get.helm.sh](https://get.helm.sh) under the
Apache-2.0 license. Anyone looking for Helm should get it from [helm.sh](https://helm.sh).

## Contents

A single layer holding `/usr/local/bin/helm`, built for `linux/amd64` and `linux/arm64/v8`. There is
no base image, no shell, and no entry point, so `docker run` on the image alone fails with
`no command specified`. Naming the binary works, because Helm is statically linked:

```bash
docker run --rm --entrypoint helm ghcr.io/hivemq/helm-test-image:<version> version
```

The intended use is different: consumers merge the image into their own test image as a parent image
dependency, which stacks the layer and leaves the other image's entry point in place.

The Helm version is read from the `.helm-version` file in the repository root, the same pin the
workflows and the manifest scripts use. The image tag is that version without the leading `v`.

## Build

```bash
./gradlew :helm-image:ociImageLayout
```

The build downloads the Helm distribution for both architectures, verifies its SHA-256 checksum
against the published sidecar file, and packs the binary into a layer. No container runtime and no
package manager are involved.

## Publish

The `Publish Helm test image to ghcr` workflow pushes the image when `.helm-version` or anything
under `helm-image/` changes on `master`, and on manual dispatch.

To push from a local checkout, provide credentials for a GitHub account with `write:packages`:

```bash
ORG_GRADLE_PROJECT_ghcrUsername=<user> \
ORG_GRADLE_PROJECT_ghcrPassword=<token> \
./gradlew :helm-image:pushOciImage
```

## Consuming the image

Consumers declare the registry and take the image as a parent image dependency:

```kotlin
oci {
    registries {
        dockerHub {
            optionalCredentials()
        }
        gitHubContainerRegistry {
            exclusiveContent {
                includeModule("hivemq", "helm-test-image")
            }
        }
    }
    imageDefinitions {
        register("main") {
            allPlatforms {
                dependencies {
                    runtime("rancher:k3s:$k3sTag")
                    runtime(ociImages.helm.oci)
                }
            }
        }
    }
}
```

with the pinned reference in `gradle/oci.versions.toml`:

```toml
[[oci]]
name = "helm"
image = "ghcr.io/hivemq/helm-test-image"
reference = "4.2.3@sha256:..."
```

`exclusiveContent` selects the single module rather than the whole `hivemq` group, so the Docker Hub
`hivemq` images keep resolving from Docker Hub.

Renovate updates that reference once a new tag is published.
