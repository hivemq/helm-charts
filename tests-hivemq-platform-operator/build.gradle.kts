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
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val hivemqVersion = libs.versions.hivemq.platform.get()
val k3sTag = resolveK3sTag()

@Suppress("UnstableApiUsage")
testing {
    suites {
        @Suppress("unused")
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                // Custom Extension
                implementation(libs.hivemq.extensionSdk)
                implementation(libs.javassist)
                implementation(libs.shrinkwrap.api)
                runtimeOnly(libs.shrinkwrap.impl)

                // Testcontainers
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.hivemq)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.testcontainers.k3s)
                implementation(libs.testcontainers.selenium)

                // Testing
                implementation(libs.assertj)
                implementation(libs.awaitility)
                implementation(libs.selenium.java)
                implementation(libs.selenium.remote.driver)

                // Misc
                runtimeOnly(libs.bouncycastle.pkix)
                runtimeOnly(libs.bouncycastle.prov)
                implementation(libs.fabric8.kubernetes.client)
                implementation(libs.gradleOci.junitJupiter)
                implementation(libs.hivemq.mqttClient)
                implementation(libs.junit.platform.launcher)
                runtimeOnly(libs.logback.classic)
                implementation(libs.netty.codec.http)
                implementation(libs.rest.assured)
                implementation(libs.slf4j.api)
            }
            targets.configureEach {
                testTask {
                    systemProperty("k3s.version.type", environment["K8S_VERSION_TYPE"] ?: "LATEST")
                    systemProperty("hivemq.tag", libs.versions.hivemq.platform.get())
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
                    runtime("com.hivemq:hivemq-enterprise:$hivemqVersion").tag("latest")
                    runtime("com.hivemq:hivemq-enterprise-k8s:$hivemqVersion").tag("k8s-latest")
                    runtime("com.hivemq:hivemq-platform-operator").tag("snapshot")
                    runtime("com.hivemq:hivemq-platform-operator-init").tag("snapshot")
                    runtime("hivemq:hivemq-operator:4.7.10").tag("latest")
                    runtime("hivemq:init-dns-wait:1.0.1").tag("latest")
                    runtime("library:busybox:latest").name("busybox").tag("latest")
                    runtime("library:nginx:1.28.0").name("nginx").tag("latest")
                    runtime("selenium:standalone-firefox:4.32.0").tag("latest")
                }
                val linuxAmd64 = platformSelector(platform("linux", "amd64"))
                val linuxArm64v8 = platformSelector(platform("linux", "arm64", "v8"))
                platformSelector = if (System.getenv("CI_RUN") != null) linuxAmd64 else linuxAmd64.and(linuxArm64v8)
            }
        }
    }
}

tasks.register("integrationTestPrepare") {
    dependsOn(tasks.named("integrationTest").get().taskDependencies.getDependencies(null))
}

/* ******************** OCI images ******************** */

val helmOciLayerLinuxAmd64 by tasks.registering(oci.dockerLayerTaskClass) {
    from = "library/ubuntu@sha256:440dcf6a5640b2ae5c77724e68787a906afb8ddee98bf86db94eea8528c2c076" // noble-20250619
    platform = oci.platform("linux", "amd64")
    command =
        "apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq && " +
                "curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 && " +
                "bash get_helm.sh && rm -rf /var/lib/apt/lists/* get_helm.sh"
    destinationDirectory = layout.buildDirectory.dir("oci/layers")
    classifier = "helm@linux,amd64"
}

val helmOciLayerLinuxArm64 by tasks.registering(oci.dockerLayerTaskClass) {
    from = "library/ubuntu@sha256:440dcf6a5640b2ae5c77724e68787a906afb8ddee98bf86db94eea8528c2c076" // noble-20250619
    platform = oci.platform("linux", "arm64", "v8")
    command =
        "apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq && " +
                "curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 && " +
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
    imageMapping {
        mapModule("com.hivemq", "hivemq-enterprise") {
            toImage("hivemq/hivemq4").withTag(version)
        }
        mapModule("com.hivemq", "hivemq-enterprise-k8s") {
            toImage("hivemq/hivemq4").withTag(version.prefix("k8s-"))
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

@Suppress("unused")
val pushAllImagesForTesting by tasks.registering(oci.pushImageTaskClass) {
    from(oci.imageDependencies.create("imagesForTesting") {
        runtime("com.hivemq:hivemq-platform-operator").tag("snapshot")
        runtime("com.hivemq:hivemq-platform-operator-init").tag("snapshot")
        runtime("hivemq:hivemq4:$hivemqVersion").tag("latest")
    })
}

/* ******************** update versions ******************** */

@Suppress("unused")
val updatePlatformVersion by tasks.registering {
    group = "version"
    val appVersion = project.properties["appVersion"]
    if (appVersion != null) {
        doLast {
            val filesToUpdate = fileTree(projectDir).matching {
                include("**/*.yml")
                include("**/*.yaml")
                include("**/*.json")
                include("**/*.sh")
                include("**/*.toml")
                include("**/*.java")
                // include test hivemq/mqtt-cli image to update, which is part of the hivemq-platform and hivemq-edge charts
            }.plus(
                files(
                    "../charts/hivemq-platform/templates/tests/test-mqtt-cli.yml",
                    "../charts/hivemq-edge/templates/tests/test-mqtt-cli.yml"
                )
            )
            filesToUpdate.forEach { file ->
                val text = file.readText()
                file.writeText(text.replace("""^hivemq-platform = \"(.*)\"$""".toRegex(RegexOption.MULTILINE)) {
                    "hivemq-platform = \"${appVersion}\""
                }.replace("""(?i)(hivemq/hivemq4:)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}${appVersion}${it.groupValues[3]}"
                }.replace("""(?i)(hivemq/mqtt-cli:)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}${appVersion}${it.groupValues[3]}"
                })
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
