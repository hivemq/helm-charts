plugins {
    java
}

group = "com.hivemq.helmcharts"

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
    implementation("org.codehaus.groovy:groovy-all:${property("groovy.version")}")
    implementation("org.junit.jupiter:junit-jupiter:${property("junit.version")}")
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
    from("container")
}

val buildRootlessK8sImage by tasks.registering(Exec::class) {
    group = "container"
    description = "Build hivemq rootless k8s image"
    dependsOn(producerK8sDockerImage)
    inputs.property("dockerImageName", containerName)
    inputs.dir(createRootlessK8sImageContext.map { it.destinationDir })
    workingDir(createRootlessK8sImageContext.map { it.destinationDir })
    commandLine("docker", "build", "-f", "broker.dockerfile", "-t", "${containerName}-rootless:${containerTag}", ".")
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
    from(producerK8sDockerImage){
        rename {"hivemq-k8s-image.tar"}
    }
    from(producerDnsInitWaitDockerImage){
        rename {"hivemq-init-dns-image.tar"}
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
