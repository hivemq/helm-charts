plugins {
    java
}

group = "com.hivemq.helmcharts"

val hivemqVersion = "${project.properties["hivemq.version"]}"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

/* ******************** test ******************** */

dependencies {
    // JUnit
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit.version")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit.version")}")

    // Custom Extension
    testImplementation("com.hivemq:hivemq-extension-sdk:${property("hivemq.extension-sdk.version")}")
    testImplementation("org.javassist:javassist:${property("javassist.version")}")
    testImplementation("org.jboss.shrinkwrap:shrinkwrap-api:${property("shrinkwrap.version")}")
    testImplementation("org.jboss.shrinkwrap:shrinkwrap-impl-base:${property("shrinkwrap.version")}")

    // Testcontainers
    testImplementation("org.testcontainers:testcontainers:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:k3s:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:hivemq:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:selenium:${property("testcontainers.version")}")

    // Certificates
    testImplementation("org.bouncycastle:bcprov-jdk15on:${property("bouncycastle.version")}")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:${property("bouncycastle.version")}")

    // Testing
    testImplementation("org.assertj:assertj-core:${property("assertj.version")}")
    testImplementation("org.awaitility:awaitility:${property("awaitility.version")}")
    testImplementation("org.seleniumhq.selenium:selenium-remote-driver:${property("selenium.version")}")
    testImplementation("org.seleniumhq.selenium:selenium-java:${property("selenium.version")}")

    // Misc
    testImplementation("io.fabric8:kubernetes-client:${property("fabric8-kubernetes-client.version")}")
    testImplementation("ch.qos.logback:logback-classic:${property("logback.version")}")
    testImplementation("ch.qos.logback:logback-core:${property("logback.version")}")
    testImplementation("org.slf4j:slf4j-api:${property("slf4j.version")}")
    testImplementation("io.rest-assured:rest-assured:${property("rest-assured.version")}")
    testImplementation("com.hivemq:hivemq-mqtt-client:${property("hivemq-client.version")}")
    testImplementation("io.netty:netty-codec-http:${property("netty-codec-http.version")}")
}

fun Test.configureJUnitPlatform() {
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
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs integration tests"
    testClassesDirs = sourceSets[name].output.classesDirs
    classpath = sourceSets[name].runtimeClasspath

    if (environment["TEST_PLAN"] != null) {
        val testPlan = environment["TEST_PLAN"].toString()
        if (testPlan == "Other") {
            systemProperty(
                "excludeTags", "Upgrade,Extensions,Services1,Services2,CustomConfig,Services,Platform,NonRootUser"
            )
        } else {
            systemProperty(
                "includeTags", testPlan
            )
        }
    }
    configureJUnitPlatform()
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
        "hivemq.version" to "${project.properties["hivemq.version"]}",
        "selenium.version" to "${project.properties["selenium.version"]}",
        "nginx.version" to "${project.properties["nginx.version"]}"
    )

    dependsOn(saveDockerImages)  // Platform Operator images

    inputs.files(
        layout.buildDirectory.file("hivemq-dns-init-wait.tar"),
        layout.buildDirectory.file("hivemq-operator.tar"),
        layout.buildDirectory.file("hivemq-k8s.tar"),
        layout.buildDirectory.file("hivemq-platform-operator-init.tar"),
        layout.buildDirectory.file("hivemq-platform-operator.tar"),
        layout.buildDirectory.file("hivemq-platform.tar"),
    )
}

sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

/* ******************** Docker Platform Operator Images ******************** */

val savePlatformOperatorDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Platform Operator Docker image"
    dependsOn(gradle.includedBuild("hivemq-platform-operator").task(":quarkusBuild"))
    workingDir(layout.buildDirectory)
    println("Saving HiveMQ Platform Operator Docker image with tag: snapshot")
    commandLine("docker", "save", "-o", "hivemq-platform-operator.tar", "hivemq/hivemq-platform-operator-test:snapshot")
}

val savePlatformOperatorInitDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Platform Operator Init Docker image"
    dependsOn(gradle.includedBuild("hivemq-platform-operator-init").task(":docker"))
    workingDir(layout.buildDirectory)
    println("Saving HiveMQ Platform Operator Init Docker image with tag: snapshot")
    commandLine(
        "docker",
        "save",
        "-o",
        "hivemq-platform-operator-init.tar",
        "hivemq/hivemq-platform-operator-init-test:snapshot"
    )
}

val savePlatformDockerImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save HiveMQ Platform Docker image"
    println("Saving HiveMQ Platform Docker image with tag: $hivemqVersion")
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
                include("**/*.adoc")
                include("**/*.md")
                include("**/*.properties")
                include("**/*.java")
                // include test hivemq/mqtt-cli image to update, which is part of the hivemq-platform chart
            }.plus(file("../charts/hivemq-platform/templates/tests/test-mqtt-cli.yml"))
            filesToUpdate.forEach { file ->
                val text = file.readText()
                file.writeText(text.replace("""^(hivemq.version)=(.*)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}=${appVersion}"
                }.replace("""^(hivemq\..*\.version)=(.*)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}=${appVersion}"
                }.replace("""(?i)(hivemq/hivemq4:)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}${appVersion}${it.groupValues[3]}"
                }.replace("""(?i)(hivemq/mqtt-cli:)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) {
                    "${it.groupValues[1]}${appVersion}${it.groupValues[3]}"
                })
            }
        }
    }
}
