plugins {
    java
}

group = "com.hivemq.helmcharts"

val testPlanOtherExcludeTags = "Extensions,K8sVersionCompatibility,RollingUpgrades"

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
                    if (environment["TEST_PLAN"] != null) {
                        val testPlan = environment["TEST_PLAN"].toString()
                        if (testPlan == "Other") {
                            systemProperty("excludeTags", testPlanOtherExcludeTags)
                        } else {
                            systemProperty("includeTags", testPlan)
                        }
                    }
                    useJUnitPlatform {
                        if (systemProperties["includeTags"] != null) {
                            val includeTags = systemProperties["includeTags"].toString().split(",")
                            println("JUnit includeTags: $includeTags")
                            includeTags(*includeTags.toTypedArray())
                        }
                        if (systemProperties["excludeTags"] != null) {
                            val excludeTags = systemProperties["excludeTags"].toString().split(",")
                            println("JUnit excludeTags: $excludeTags")
                            excludeTags(*excludeTags.toTypedArray())
                        }
                    }
                    testLogging {
                        events("started", "passed", "skipped", "failed")
                        showStandardStreams = true
                    }
                    reports {
                        junitXml.isOutputPerTestCase = true
                    }
                    maxHeapSize = "3g"

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

val listIntegrationTests by tasks.registering(JavaExec::class) {
    val usage = "Usage: ./gradlew listIntegrationTests -PtestPlan=xxx" +
            "\n\t\t- 'testPlan': The test plan from the GitHub action. Mandatory."
    group = "help"
    description = "Lists all integration tests from the given test plan.\n\t$usage"
    classpath = sourceSets["integrationTest"].runtimeClasspath
    mainClass = "com.hivemq.helmcharts.util.JUnitUtil"
    doFirst {
        val testPlan: String = (project.properties["testPlan"] ?: error("`testPlan` must be set\n\n$usage")).toString()
        var includeTags = testPlan
        var excludeTags = ""
        if (testPlan == "Other") {
            includeTags = ""
            excludeTags = testPlanOtherExcludeTags
        }
        args = listOf("com.hivemq.helmcharts", includeTags, excludeTags, "true")
    }
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
    commandLine("docker", "build", "-f", "example_nonroot_k8s.dockerfile", "-t", "${containerName}-rootless:${containerTag}", ".")
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
            }.plus(fileTree("../examples/hivemq-operator").matching{
                include("**/*.yml")
                include("**/*.yaml")
            })
            filesToUpdate.forEach { file ->
                val text = file.readText()
                file.writeText(text.replace("""^hivemq-platform = \"(.*)\"$""".toRegex(RegexOption.MULTILINE)) {
                    "hivemq-platform = \"${appVersion}\""
                }.replace("""(?i)(hivemq/hivemq4:k8s-)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}${appVersion}${it.groupValues[3]}"
                })
            }
        }
    }
}
