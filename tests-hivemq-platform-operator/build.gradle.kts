plugins {
    java
}

group = "com.hivemq.helmcharts"

val testPlanOtherExcludeTags = "Containers,ContainerSecurityContext,CustomConfig,CustomOperatorConfig,Extensions,Licenses,Monitoring,Platform,PodSecurityContext,ServiceAccount,Services,Upgrade,Volumes"

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
                // Custom Extension
                implementation(libs.hivemq.extensionSdk)
                implementation(libs.javassist)
                implementation(libs.shrinkwrap.api)
                runtimeOnly(libs.shrinkwrap.impl)

                // Testcontainers
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.k3s)
                implementation(libs.testcontainers.hivemq)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.testcontainers.selenium)

                // Testing
                implementation(libs.assertj)
                implementation(libs.awaitility)
                implementation(libs.selenium.remote.driver)
                implementation(libs.selenium.java)

                // Misc
                implementation(libs.fabric8.kubernetes.client)
                runtimeOnly(libs.bouncycastle.pkix)
                runtimeOnly(libs.bouncycastle.prov)
                implementation(libs.junit.platform.launcher)
                implementation(libs.slf4j.api)
                runtimeOnly(libs.logback.classic)
                implementation(libs.rest.assured)
                implementation(libs.hivemq.mqttClient)
                implementation(libs.netty.codec.http)
            }
            targets.configureEach {
                testTask {
                    if (environment["TEST_PLAN"] != null) {
                        val testPlan = environment["TEST_PLAN"].toString()
                        if (testPlan == "Other") {
                            systemProperty(
                                "excludeTags",
                                testPlanOtherExcludeTags,
                            )
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

                    // sets docker images versions for the tests
                    systemProperties(
                        "hivemq.version" to libs.versions.hivemq.platform.get(),
                        "selenium.version" to libs.versions.selenium.get(),
                        "nginx.version" to libs.versions.nginx.container.get()
                    )

                    dependsOn(saveDockerImages)
                    inputs.files(
                        layout.buildDirectory.file("hivemq-dns-init-wait.tar"),
                        layout.buildDirectory.file("hivemq-operator.tar"),
                        layout.buildDirectory.file("hivemq-k8s.tar"),
                        layout.buildDirectory.file("hivemq-platform-operator-init.tar"),
                        layout.buildDirectory.file("hivemq-platform-operator.tar"),
                        layout.buildDirectory.file("hivemq-platform.tar"),
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

/* ******************** Docker Platform Operator Images ******************** */

val savePlatformOperatorDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Platform Operator Docker image"
    dependsOn(gradle.includedBuild("hivemq-platform-operator").task(":quarkusBuild"))
    workingDir(layout.buildDirectory)
    commandLine("docker", "save", "-o", "hivemq-platform-operator.tar", "hivemq/hivemq-platform-operator-test:snapshot")
}

val savePlatformOperatorInitDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Platform Operator Init Docker image"
    dependsOn(gradle.includedBuild("hivemq-platform-operator-init").task(":docker"))
    workingDir(layout.buildDirectory)
    commandLine(
        "docker",
        "save",
        "-o",
        "hivemq-platform-operator-init.tar",
        "hivemq/hivemq-platform-operator-init-test:snapshot"
    )
}

val hivemqVersion = libs.versions.hivemq.platform.get()

val savePlatformDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Platform Docker image"
    dependsOn(pullPlatformDockerImage)
    workingDir(layout.buildDirectory)
    commandLine("docker", "save", "-o", "hivemq-platform.tar", "docker.io/hivemq/hivemq4:$hivemqVersion")
}

val pullPlatformDockerImage by tasks.registering(Exec::class) {
    commandLine("docker", "pull", "docker.io/hivemq/hivemq4:$hivemqVersion")
}

val saveDockerImages by tasks.registering {
    group = "container"
    description = "Save all Platform Docker images"
    dependsOn(savePlatformOperatorInitDockerImage)
    dependsOn(savePlatformOperatorDockerImage)
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
                // include test hivemq/mqtt-cli image to update, which is part of the hivemq-platform chart
            }.plus(file("../charts/hivemq-platform/templates/tests/test-mqtt-cli.yml"))
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
