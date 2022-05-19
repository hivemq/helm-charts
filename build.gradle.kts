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
    maven{
        url = uri("https://jitpack.io")
    }
}
val hivemq: Configuration by configurations.creating { isCanBeConsumed = false; isCanBeResolved = false }

dependencies {
    hivemq("com.hivemq:hivemq")
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
    maxHeapSize="6g"
    println(allJvmArgs)
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
val containerName = findProperty("containerName") ?: "hivemq4-k8s-test"
val containerTag = findProperty("containerTag") ?: "snapshot"

val createImageContext by tasks.registering(Sync::class) {
    group = "container"
    description = "Prepare container base image context"
    into(layout.buildDirectory.dir("container/context"))
    from("container")
}

val buildImage by tasks.registering(Exec::class){
    group = "container"
    description = "Build docker image"
    inputs.property("dockerImageName", containerName)
    inputs.dir(createImageContext.map { it.destinationDir })
    workingDir(createImageContext.map { it.destinationDir })
    commandLine("docker","build","-f","broker.dockerfile","-t","${containerName}:${containerTag}",".")
    println("${containerName}:${containerTag}")

}

val saveImage by tasks.registering(Exec::class){
    dependsOn(buildImage)
    group = "container"
    description = "Save docker image"
    workingDir(createImageContext.map { it.destinationDir })
    commandLine("docker","save","-o","${containerName}.tar","${containerName}:${containerTag}")
    println("Image Saved")
}
val pullBaseImages by tasks.registering(Exec::class){
    group = "container"
    description = "Download support images"
    commandLine("docker","pull","busybox:1.35.0")
}

tasks.named("integrationTest"){
    println("Run integration tests")
    //dependsOn(saveImage)
}

val producerDockerImage: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("k8s-docker-image"))
    }
}

tasks.register<Task>("buildContainers"){
    group = "container"
    /*dependsOn(gradle.includedBuild("hivemq").task(":buildK8sImage"))
    dependsOn(gradle.includedBuild("hivemq-operator").task(":buildDnsInitWaitImage"))
    dependsOn(gradle.includedBuild("hivemq-operator").task(":jibDockerBuild"))*/
    doLast{
        println("containers")
        println(producerDockerImage.resolvedConfiguration.hasError())
        println(producerDockerImage.state)
        println(producerDockerImage.files.size)
        println(producerDockerImage.hierarchy)
    }
}