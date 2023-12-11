plugins {
    java
}

group = "com.hivemq.helmcharts"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "operator"
        url = uri("https://maven.pkg.github.com/hivemq/hivemq-operator")
        credentials(PasswordCredentials::class)
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

val hivemq: Configuration by configurations.creating { isCanBeConsumed = false; isCanBeResolved = false }
val operator: Configuration by configurations.creating { isCanBeConsumed = false; isCanBeResolved = false }

dependencies {
    hivemq("com.hivemq:hivemq")
    operator("com.hivemq:hivemq-operator")
    testImplementation("org.codehaus.groovy:groovy-all:${property("groovy.version")}")
    testImplementation("org.junit.jupiter:junit-jupiter:${property("junit.version")}")
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit.version")}")
    testImplementation("org.testcontainers:testcontainers:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:k3s:${property("testcontainers.version")}")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainers.version")}")
    testImplementation("org.slf4j:slf4j-api:${property("slf4j.version")}")
    testImplementation("org.slf4j:slf4j-simple:${property("slf4j.version")}")
    testImplementation("com.hivemq:hivemq-mqtt-client:${property("hivemq.client.version")}")
    testImplementation("com.hivemq.operator:operator:${property("hivemq.operator.version")}")
    testImplementation("io.fabric8:kubernetes-client:${property("fabric8.version")}")
    testImplementation("org.bouncycastle:bcprov-jdk15on:${property("bouncycastle.version")}")
    testImplementation("org.bouncycastle:bcpkix-jdk15on:${property("bouncycastle.version")}")
    testImplementation("org.awaitility:awaitility:${property("awaitility.version")}")
    testImplementation("ch.qos.logback:logback-classic:${property("logback.version")}")
    testImplementation("ch.qos.logback:logback-core:${property("logback.version")}")
    testImplementation("org.slf4j:slf4j-api:${property("slf4j.version")}")
    testImplementation("org.testcontainers:hivemq:${property("testcontainers.version")}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "6g"
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs integration tests"
    testClassesDirs = sourceSets[name].output.classesDirs
    classpath = sourceSets[name].runtimeClasspath
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
val containerName = findProperty("containerName") ?: "hivemq-k8s-test"
val containerTag = findProperty("containerTag") ?: "snapshot"

val producerK8sDockerImage: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("k8s-docker-image"))
    }
    extendsFrom(hivemq)
}
val producerDnsInitWaitDockerImage: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("dns-init-wait-docker-image"))
    }
    extendsFrom(operator)
}
val producerOperatorDockerImage: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("operator-docker-image"))
    }
    extendsFrom(operator)
}


val createRootlessK8sImageContext by tasks.registering(Sync::class) {
    group = "container"
    description = "Prepare hivemq rootless k8s image context"
    into(layout.buildDirectory.dir("container/context"))
    from("examples")
}

val buildRootlessK8sImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Build hivemq rootless k8s image"
    dependsOn(producerK8sDockerImage)
    inputs.property("dockerImageName", containerName)
    inputs.dir(createRootlessK8sImageContext.map { it.destinationDir })
    workingDir(createRootlessK8sImageContext.map { it.destinationDir })
    commandLine("docker", "build", "-f", "example_nonroot_k8s.dockerfile", "-t", "${containerName}-rootless:${containerTag}", ".")
}

val saveRootlessK8sImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Save hivemq rootless k8s image"
    dependsOn(buildRootlessK8sImage)
    workingDir(layout.buildDirectory.dir("containers"))
    commandLine("docker", "save", "-o", "${containerName}-rootless.tar", "${containerName}-rootless:${containerTag}")
}
val buildContainersFiles by tasks.registering(Copy::class) {
    group = "container"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(producerK8sDockerImage) {
        rename { "hivemq-k8s-image.tar" }
    }
    from(producerDnsInitWaitDockerImage) {
        rename { "hivemq-init-dns-image.tar" }
    }
    from(producerOperatorDockerImage) {
        rename { "hivemq-operator.tar" }
    }
    from(
            producerK8sDockerImage.singleFile,
            producerDnsInitWaitDockerImage.singleFile,
            producerOperatorDockerImage.singleFile
    )
    into(layout.buildDirectory.dir("containers"))
}

buildContainersFiles {
    finalizedBy(saveRootlessK8sImage)
}

tasks.named("integrationTest") {
   dependsOn(buildContainersFiles)
}

val updateOperatorChartVersion by tasks.registering {
    group = "version"
    project.ext.set("versionFilesToUpdate", arrayOf("charts/hivemq-operator/Chart.yaml", "charts/hivemq-operator/values.yaml"))
    project.ext.set("valuesRegex", """(image:\s+[^:]+:\w+-)(\S+)""")
    dependsOn(updateChartAndValueFilesWithVersion)
}

val updateSwarmChartVersion by tasks.registering {
    group = "version"
    project.ext.set("versionFilesToUpdate", arrayOf("charts/hivemq-swarm/Chart.yaml", "charts/hivemq-swarm/values.yaml"))
    project.ext.set("valuesRegex", """(tag:\s*)(\S+)""")
    dependsOn(updateChartAndValueFilesWithVersion)
}

val updateChartAndValueFilesWithVersion by tasks.registering {
    group = "version"
    doLast {
        val filesToUpdate = files(project.properties["versionFilesToUpdate"])
        val chartVersion = project.properties["chartVersion"]
        val appVersion = project.properties["appVersion"]
        val valuesRegex = project.properties["valuesRegex"] as String
        if (chartVersion == null) {
            error("`chartVersion` must be set")
        }
        filesToUpdate.filter { file -> file.name == "Chart.yaml" }.forEach {
            var replacedTextAppVersion = it.readText()
            if (appVersion != null) {
                replacedTextAppVersion = replacedTextAppVersion.replace("""(?m)^appVersion:\s*\S+$""".toRegex(), "appVersion: $appVersion")
            }
            val replacedChartVersion = replacedTextAppVersion.replace("""(?m)^version:\s*\S+$""".toRegex(), "version: $chartVersion")
            it.writeText(replacedChartVersion)
        }
        if (appVersion != null) {
            filesToUpdate.filter { file -> file.name == "values.yaml" }.forEach {
                val text = it.readText()
                val replacedText = text.replace(valuesRegex.toRegex()) { matchResult ->
                    "${matchResult.groupValues[1]}${appVersion}"
                }
                it.writeText(replacedText)
            }
        }
    }
}
