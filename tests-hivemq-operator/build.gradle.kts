import com.fasterxml.jackson.dataformat.toml.TomlMapper

plugins {
    java
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

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.jackson.dataformat.toml)
    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                implementation(libs.assertj)
                implementation(libs.awaitility)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.k3s)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.fabric8.kubernetes.client)
                runtimeOnly(libs.bouncycastle.pkix)
                runtimeOnly(libs.bouncycastle.prov)
                implementation(libs.junit.platform.launcher)
                implementation(libs.hivemq.mqttClient)
                implementation(libs.slf4j.api)
                runtimeOnly(libs.logback.classic)
            }
            targets.configureEach {
                testTask {
                    testLogging {
                        events("started", "passed", "skipped", "failed")
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
                        layout.buildDirectory.file("hivemq-dns-init-wait.tar"),
                        layout.buildDirectory.file("hivemq-operator.tar"),
                        layout.buildDirectory.file("hivemq-k8s.tar"),
                    )
                }
            }
        }
    }
}

tasks.register("integrationTestPrepare") {
    dependsOn(tasks.named("integrationTest").get().taskDependencies.getDependencies(null))
}

/* ******************** Legacy Docker Operator Images ******************** */

val containerName = findProperty("containerName") ?: "hivemq-k8s-test"
val containerTag = findProperty("containerTag") ?: "snapshot"

val createRootlessK8sImageContext by tasks.registering(Sync::class) {
    group = "container"
    description = "Prepare HiveMQ rootless K8s image context"
    into(layout.buildDirectory.dir("container/context"))
    from("../examples/hivemq-operator")
}

val buildRootlessK8sImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Build HiveMQ rootless K8s image"
    inputs.property("dockerImageName", containerName)
    inputs.dir(createRootlessK8sImageContext.map { it.destinationDir })
    workingDir(createRootlessK8sImageContext.map { it.destinationDir })
    commandLine(
        "docker",
        "build",
        "-f",
        "example_nonroot_k8s.dockerfile",
        "-t",
        "${containerName}-rootless:${containerTag}",
        "."
    )
}

val saveRootlessK8sImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ rootless K8s image"
    dependsOn(buildRootlessK8sImage)
    workingDir(layout.buildDirectory)
    commandLine("docker", "save", "-o", "${containerName}-rootless.tar", "${containerName}-rootless:${containerTag}")
}

val saveLegacyOperatorDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Legacy Operator Docker image"
    dependsOn(gradle.includedBuild("hivemq-operator").task(":jibDockerBuild"))
    workingDir(layout.buildDirectory)
    commandLine("docker", "save", "-o", "hivemq-operator.tar", "hivemq/hivemq-operator:snapshot")
}

val saveDnsInitWaitDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ DNS Init Wait Docker image"
    dependsOn(gradle.includedBuild("hivemq-operator").task(":buildDnsInitWaitImage"))
    workingDir(layout.buildDirectory)
    commandLine("docker", "save", "-o", "hivemq-dns-init-wait.tar", "hivemq/init-dns-wait:snapshot")
}

val k8sVersion = "k8s-${libs.versions.hivemq.platform.get()}"

val saveK8sDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ K8s Docker image"
    dependsOn(pullK8sDockerImage)
    workingDir(layout.buildDirectory)
    commandLine("docker", "save", "-o", "hivemq-k8s.tar", "docker.io/hivemq/hivemq4:$k8sVersion")
}

val pullK8sDockerImage by tasks.registering(Exec::class) {
    commandLine("docker", "pull", "docker.io/hivemq/hivemq4:$k8sVersion")
}

val saveDockerImages by tasks.registering {
    group = "container"
    description = "Save all Legacy Docker images"
    dependsOn(saveK8sDockerImage)
    dependsOn(saveDnsInitWaitDockerImage)
    dependsOn(saveLegacyOperatorDockerImage)
    dependsOn(saveRootlessK8sImage)
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
            }.plus(fileTree("../examples/hivemq-operator").matching {
                include("**/*.yml")
                include("**/*.yaml")
                // include test hivemq/mqtt-cli image to update, which is part of the hivemq-operator chart
            }).plus(file("../charts/hivemq-operator/templates/tests/test-mqtt-cli.yml"))
            filesToUpdate.forEach { file ->
                val text = file.readText()
                file.writeText(text.replace("""^hivemq-platform = \"(.*)\"$""".toRegex(RegexOption.MULTILINE)) {
                    "hivemq-platform = \"${appVersion}\""
                }.replace("""(?i)(hivemq/hivemq4:k8s-)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}${appVersion}${it.groupValues[3]}"
                }.replace("""(?i)(hivemq/mqtt-cli:)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}${appVersion}${it.groupValues[3]}"
                })
            }
        }
    }
}
