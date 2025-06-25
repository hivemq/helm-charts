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
                    doFirst {
                        // sets Docker image tags for the tests
                        val tomlFile = projectDir.resolve("gradle").resolve("docker.versions.toml")
                        val tomlDocker = TomlMapper().readTree(tomlFile).path("docker")
                        tomlDocker.fields().forEach { (key, value) ->
                            val tag = value.path("tag").asText()
                            if (tag.isNotEmpty()) {
                                println("Configuring test Docker image $key:$tag")
                                systemProperty("$key.tag", tag)
                            }
                        }
                        systemProperty("hivemq.tag", libs.versions.hivemq.platform.get())
                        val k8sVersionType = environment["K8S_VERSION_TYPE"] ?: "LATEST"
                        val key = if (k8sVersionType == "MINIMUM") "k3s-minimum" else "k3s-latest"
                        val tag = tomlDocker.path(key).path("tag").asText()
                        println("Configuring test Docker image k3s:$tag ($k8sVersionType)")
                        systemProperty("k3s.tag", tag)
                        systemProperty("k3s.version.type", k8sVersionType)
                    }
                    dependsOn(saveDockerImages)
                    inputs.files(
                        layout.buildDirectory.file("test-image-tars/hivemq-platform-operator-init.tar"),
                        layout.buildDirectory.file("test-image-tars/hivemq-platform-operator.tar"),
                        layout.buildDirectory.file("test-image-tars/hivemq-platform.tar"),
                    )
                }
            }
            oci.of(this) {
                imageDependencies {
                    runtime(project).tag("latest")
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

/* ******************** OCI Platform Operator Images ******************** */

val helmOciLayerLinuxAmd64 by tasks.registering(oci.dockerLayerTaskClass) {
    from = "library/ubuntu@sha256:6015f66923d7afbc53558d7ccffd325d43b4e249f41a6e93eef074c9505d2233"
    platform = oci.platform("linux", "amd64")
    command = "apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq && " +
            "curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 && " +
            "bash get_helm.sh && rm -rf /var/lib/apt/lists/* get_helm.sh"
    destinationDirectory = layout.buildDirectory.dir("oci/layers")
    classifier = "helm@linux,amd64"
}

val helmOciLayerLinuxArm64 by tasks.registering(oci.dockerLayerTaskClass) {
    from = "library/ubuntu@sha256:6015f66923d7afbc53558d7ccffd325d43b4e249f41a6e93eef074c9505d2233"
    platform = oci.platform("linux", "arm64", "v8")
    command = "apt-get update && apt-get install --no-install-recommends curl apt-transport-https ca-certificates -yq && " +
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
    imageDefinitions {
        register("main") {
            imageTag = provider { project.version.toString().lowercase() }
            allPlatforms {
                dependencies {
                    runtime("rancher:k3s:sha256!a2920ca992f197e35812173322734207b89f73084dbe3e2404c11d582a9ecf40") // v1.33.1-k3s1
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

val operatorImageLayoutForTesting by tasks.registering(oci.imagesLayoutTaskClass) {
    from(oci.imageDependencies.create("operatorImageTarForTesting") {
        runtime("com.hivemq:hivemq-platform-operator").name("hivemq/hivemq-platform-operator-test")
            .tag("snapshot")
    })
    destinationDirectory = layout.buildDirectory.dir("test-image-tars")
    classifier = "hivemq-platform-operator"
}

val operatorImageLayoutForTestingTar by tasks.existing(Tar::class) {
    destinationDirectory = layout.buildDirectory.dir("test-image-tars")
    archiveFileName = "hivemq-platform-operator.tar"
}

val initImageLayoutForTesting by tasks.registering(oci.imagesLayoutTaskClass) {
    from(oci.imageDependencies.create("initImageTarForTesting") {
        runtime("com.hivemq:hivemq-platform-operator-init").name("hivemq/hivemq-platform-operator-init-test")
            .tag("snapshot")
    })
    destinationDirectory = layout.buildDirectory.dir("test-image-tars")
    classifier = "hivemq-platform-operator-init"
}

val initImageLayoutForTestingTar by tasks.existing(Tar::class) {
    destinationDirectory = layout.buildDirectory.dir("test-image-tars")
    archiveFileName = "hivemq-platform-operator-init.tar"
}

val hivemqVersion = libs.versions.hivemq.platform.get()

val savePlatformDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Platform Docker image"
    dependsOn(pullPlatformDockerImage)
    val outputDir = layout.buildDirectory.dir("test-image-tars").get().asFile
    doFirst {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }
    workingDir(outputDir)
    commandLine("docker", "save", "-o", "hivemq-platform.tar", "docker.io/hivemq/hivemq4:$hivemqVersion")
    outputs.file(outputDir.resolve("hivemq-platform.tar"))
}

val pullPlatformDockerImage by tasks.registering(Exec::class) {
    commandLine("docker", "pull", "docker.io/hivemq/hivemq4:$hivemqVersion")
}

val saveDockerImages by tasks.registering {
    group = "container"
    description = "Save all Platform Docker images"
    dependsOn(operatorImageLayoutForTestingTar)
    dependsOn(initImageLayoutForTestingTar)
    dependsOn(savePlatformDockerImage)
}

/* ******************** update versions ******************** */

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
            }.plus(files("../charts/hivemq-platform/templates/tests/test-mqtt-cli.yml", "../charts/hivemq-edge/templates/tests/test-mqtt-cli.yml"))
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
