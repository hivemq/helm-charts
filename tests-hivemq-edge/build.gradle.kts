import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.jackson.dataformat.toml)
    }
}

plugins {
    java
    alias(libs.plugins.oci)
}

group = "com.hivemq.helmcharts"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

configurations.all {
    exclude("io.fabric8", "kubernetes-httpclient-vertx")
}

val hivemqEdgeVersion = libs.versions.hivemq.edge.get()
val k3sTag = resolveK3sTag()

@Suppress("UnstableApiUsage")
testing {
    suites {
        @Suppress("unused")
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                runtimeOnly(libs.junit.platform.launcher)

                // K8s
                implementation(libs.kubernetes.client)
                implementation(libs.kubernetes.client.jdk)

                // testcontainers
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.testcontainers.k3s)

                // testing
                implementation(libs.assertj)
                implementation(libs.awaitility)

                // misc
                implementation(libs.gradleOci.junitJupiter)
                implementation(libs.hivemq.mqttClient)
                runtimeOnly(libs.logback.classic)
                implementation(libs.rest.assured)
                implementation(libs.slf4j.api)
            }
            targets.configureEach {
                testTask {
                    jvmArgumentProviders.add(CommandLineArgumentProvider {
                        listOf(
                            // see https://netty.io/wiki/java-24-and-sun.misc.unsafe.html
                            "--enable-native-access=ALL-UNNAMED",
                            "--sun-misc-unsafe-memory-access=allow",
                        )
                    })
                    systemProperty("k3s.version.type", environment["K8S_VERSION_TYPE"] ?: "LATEST")
                    systemProperty("hivemq.edge.tag", libs.versions.hivemq.edge.get())
                    systemProperty("junit.jupiter.execution.timeout.mode", "disabled_on_debug")
                    systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
                    testLogging {
                        events = setOf(
                            TestLogEvent.STARTED,
                            TestLogEvent.PASSED,
                            TestLogEvent.SKIPPED,
                            TestLogEvent.FAILED,
                            TestLogEvent.STANDARD_ERROR,
                        )
                        exceptionFormat = TestExceptionFormat.FULL
                        showStandardStreams = true
                    }
                    reports {
                        junitXml.isOutputPerTestCase = true
                    }
                    maxHeapSize = "3g"
                }
            }
            oci.of(this) {
                imageDependencies {
                    runtime(project).name("hivemq/helm-charts").tag("latest")
                    runtime("com.hivemq:hivemq-edge:$hivemqEdgeVersion").tag("latest")
                }
                val linuxAmd64 = platformSelector(platform("linux", "amd64"))
                val linuxArm64v8 = platformSelector(platform("linux", "arm64", "v8"))
                platformSelector = if (System.getenv("CI_RUN") != null //
                    || System.getProperty("os.arch", "").equals("amd64")
                ) linuxAmd64 else linuxAmd64.and(linuxArm64v8)
            }
        }
    }
}

tasks.register("integrationTestPrepare") {
    dependsOn(provider {
        tasks.named("integrationTest").get().taskDependencies.getDependencies(null)
    })
}

/* ******************** OCI images ******************** */

val helmOciLayerLinuxAmd64 by tasks.registering(oci.dockerLayerTaskClass) {
    dependencies(oci.parentImageDependencies["noble"])
    platform = oci.platform("linux", "amd64")
    command =
        "apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq && " +
                "curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-4 && " +
                "bash get_helm.sh && rm -rf /var/lib/apt/lists/* get_helm.sh"
    destinationDirectory = layout.buildDirectory.dir("oci/layers")
    classifier = "helm@linux,amd64"
}

val helmOciLayerLinuxArm64 by tasks.registering(oci.dockerLayerTaskClass) {
    dependencies(oci.parentImageDependencies["noble"])
    platform = oci.platform("linux", "arm64", "v8")
    command =
        "apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq && " +
                "curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-4 && " +
                "bash get_helm.sh && rm -rf /var/lib/apt/lists/* get_helm.sh"
    destinationDirectory = layout.buildDirectory.dir("oci/layers")
    classifier = "helm@linux,arm64,v8"
}

oci {
    registries {
        dockerHub {
            optionalCredentials()
        }
    }
    parentImageDependencies {
        create("noble") {
            // https://hub.docker.com/layers/library/ubuntu/noble/
            runtime("library:ubuntu:sha256!d1e2e92c075e5ca139d51a140fff46f84315c0fdce203eab2807c7e495eff4f9") // noble
        }
    }
    imageDefinitions {
        register("main") {
            imageTag = provider { project.version.toString().lowercase() }
            allPlatforms {
                dependencies {
                    runtime("rancher:k3s:$k3sTag")
                }
            }
            specificPlatform(platform("linux", "amd64")) {
                layer("helm") {
                    contents(helmOciLayerLinuxAmd64)
                }
            }
            specificPlatform(platform("linux", "arm64", "v8")) {
                layer("helm") {
                    contents(helmOciLayerLinuxArm64)
                }
            }
        }
    }
}

fun resolveK3sTag(): String {
    val tomlFile = projectDir.resolve("gradle").resolve("docker.versions.toml")
    val tomlDocker = TomlMapper().readTree(tomlFile).path("docker")

    val k8sVersionType = System.getenv("K8S_VERSION_TYPE") ?: "LATEST"
    val key = if (k8sVersionType == "MINIMUM") "k3s-minimum" else "k3s-latest"
    val tag = tomlDocker.path(key).path("tag").asText()
    println("Resolving test OCI image k3s:$tag ($k8sVersionType)")
    return tag
}
