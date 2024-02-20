plugins {
    java
}

group = "com.hivemq.helmcharts"

val k8sVersion = "k8s-${project.properties["hivemq.version"]}"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

/* ******************** test ******************** */

dependencies {
    testImplementation("org.assertj:assertj-core:${property("assertj.version")}")
    testImplementation("org.codehaus.groovy:groovy-all:${property("groovy.version")}")
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit.version")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit.version")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:k3s:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainers.version")}")
    testImplementation("org.slf4j:slf4j-api:${property("slf4j.version")}")
    testImplementation("org.slf4j:slf4j-simple:${property("slf4j.version")}")
    testImplementation("com.hivemq:hivemq-mqtt-client:${property("hivemq-client.version")}")
    testImplementation("io.fabric8:kubernetes-client:${property("fabric8.version")}")
    testImplementation("org.bouncycastle:bcprov-jdk15on:${property("bouncycastle.version")}")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:${property("bouncycastle.version")}")
    testImplementation("org.awaitility:awaitility:${property("awaitility.version")}")
    testImplementation("ch.qos.logback:logback-classic:${property("logback.version")}")
    testImplementation("ch.qos.logback:logback-core:${property("logback.version")}")
    testImplementation("org.slf4j:slf4j-api:${property("slf4j.version")}")
    testImplementation("org.testcontainers:hivemq:${property("testcontainers.version")}")
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
                "excludeTags", "K8sVersionCompatibility,Extensions,RollingUpgrades"
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

    dependsOn(saveDockerImages)  // Platform Operator images

    inputs.files(
        layout.buildDirectory.file("hivemq-dns-init-wait.tar"),
        layout.buildDirectory.file("hivemq-operator.tar"),
        layout.buildDirectory.file("hivemq-k8s.tar"),
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
    println("Saving HiveMQ DNS Init Wait Docker image with tag: snapshot")
    commandLine("docker", "save", "-o", "hivemq-dns-init-wait.tar", "hivemq/init-dns-wait:snapshot")
}

val saveK8sDockerImage by tasks.registering {
    group = "container"
    description = "Save HiveMQ K8s Docker image"
    println("Saving HiveMQ K8s Docker image with tag: $k8sVersion")
    dependsOn(pullK8sDockerImage)
    doLast {
        exec {
            workingDir(layout.buildDirectory)
            commandLine("docker", "save", "-o", "hivemq-k8s.tar", "docker.io/hivemq/hivemq4:$k8sVersion")
        }
    }
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
                include("**/*.adoc")
                include("**/*.md")
                include("**/*.properties")
                include("**/*.java")
            }
            filesToUpdate.filter{ file -> "gradle.properties" == file.name }.forEach {
                val text = it.readText()
                val replacedText = text.replace("""^version=.+""".toRegex(), "version=${appVersion}")
                it.writeText(replacedText)
            }
            filesToUpdate.forEach {
                val text = it.readText()
                val replacedText1 = text.replace("""^(hivemq.version)=(.*)$""".toRegex(RegexOption.MULTILINE)) { matchResult ->
                    "${matchResult.groupValues[1]}=${appVersion}"
                }
                val replacedText2 = replacedText1.replace("""^(hivemq\..*\.version)=(.*)$""".toRegex(RegexOption.MULTILINE)) { matchResult ->
                    "${matchResult.groupValues[1]}=${appVersion}"
                }
                val replacedText3 = replacedText2.replace("""(?i)(hivemq/hivemq4:k8s-)(\d+\.\d+\.\d+(-snapshot)?)$""".toRegex(RegexOption.MULTILINE)) { matchResult ->
                    "${matchResult.groupValues[1]}${appVersion}${matchResult.groupValues[3]}"
                }
                it.writeText(replacedText3)
            }
        }
    }
}
