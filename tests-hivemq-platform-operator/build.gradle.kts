import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    alias(libs.plugins.download)
    alias(libs.plugins.hivemq.oci.version.catalog)
    alias(libs.plugins.oci)
}

group = "com.hivemq.helmcharts"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

configurations.all {
    exclude("io.fabric8", "kubernetes-httpclient-vertx")
}

val hivemqVersion = libs.versions.hivemq.platform.get()
val k3sTag = resolveK3sTag()

/*
 * Tests the HiveMQ Platform on a Java runtime other than the one of the official image.
 *
 * When the `customPlatformImageVariant` property is set, a HiveMQ Platform image is built from the latest platform
 * distribution on top of the Java runtime base image of that variant, and the integration tests run against that image
 * instead of the official one. Only the tests tagged with `custom-platform-image` are executed.
 *
 * Without the property, the build behaves as it does for every regular test run.
 */
val customPlatformImageVariant: String? = providers.gradleProperty("customPlatformImageVariant").orNull

val jreBaseImages = mapOf(
    "temurin21-resolute" to ociImages.jre.temurin21.resolute,
    "temurin21-ubi9" to ociImages.jre.temurin21.ubi9,
    "temurin25-ubi10" to ociImages.jre.temurin25.ubi10,
    "semeru21-noble" to ociImages.jre.semeru21.noble,
    "semeru25-noble" to ociImages.jre.semeru25.noble,
    "corretto21-al2023" to ociImages.jre.corretto21.al2023,
    "corretto25-al2023" to ociImages.jre.corretto25.al2023,
)

/*
 * The Java runtime base image is declared under its own module coordinate and resolved through an image mapping.
 *
 * The HiveMQ Platform Operator image builds on a Java runtime image of the same module. Declaring that module a second
 * time, with a different reference, would let Gradle's conflict resolution pick a single version for both images, so
 * the operator would silently run on the Java runtime of the variant under test.
 */
val jreBaseImageGroup = "custom-platform-image-jre"

val customPlatformImageTag = "custom-platform-image"

@Suppress("unused")
val printCustomPlatformImageVariants by tasks.registering {
    group = "verification"
    description = "Prints the custom platform image variants as a JSON array, used to build the workflow matrix"
    val variants = jreBaseImages.keys.sorted()
    doLast {
        println(variants.joinToString("\", \"", "[\"", "\"]"))
    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        @Suppress("unused")
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                runtimeOnly(libs.junit.platform.launcher)

                // K8s
                implementation(libs.kubernetes.client)
                implementation(libs.kubernetes.client.jdk)

                // custom extension
                implementation(libs.hivemq.extensionSdk)
                implementation(libs.javassist)
                implementation(libs.shrinkwrap.api)
                runtimeOnly(libs.shrinkwrap.impl)

                // testcontainers
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.hivemq)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.testcontainers.k3s)
                implementation(libs.testcontainers.selenium)

                // testing
                implementation(libs.assertj)
                implementation(libs.awaitility)
                implementation(libs.selenium.java)
                implementation(libs.selenium.remote.driver)

                // misc
                implementation(platform(libs.bouncycastle.bom))
                runtimeOnly(libs.bouncycastle.pkix)
                runtimeOnly(libs.bouncycastle.prov)
                implementation(libs.gradleOci.junitJupiter)
                implementation(libs.hivemq.mqttClient)
                runtimeOnly(libs.logback.classic)
                implementation(libs.rest.assured)
                implementation(libs.slf4j.api)
            }
            targets.configureEach {
                testTask {
                    jvmArgumentProviders.add(CommandLineArgumentProvider {
                        listOf(
                            // see https://netty.io/wiki/java-24-and-sun.misc.unsafe.html
                            "--enable-native-access=ALL-UNNAMED",
                            "--sun-misc-unsafe-memory-access=allow",
                        )
                    })
                    if (customPlatformImageVariant != null) {
                        options { (this as JUnitPlatformOptions).includeTags(customPlatformImageTag) }
                        systemProperty("custom.platform.image.variant", customPlatformImageVariant)
                    }
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
                    if (System.getenv("CI_RUN") == "true") {
                        develocity.testRetry {
                            maxRetries = 2
                            maxFailures = 6
                            failOnPassedAfterRetry = false
                        }
                    }
                    maxHeapSize = "3g"
                }
            }
            oci.of(this) {
                imageDependencies {
                    runtime(project()).name("hivemq/helm-charts").tag("latest")
                    runtime("com.hivemq:hivemq-platform-operator").tag("snapshot")
                    runtime("com.hivemq:hivemq-platform-operator-init").tag("snapshot")
                    if (customPlatformImageVariant == null) {
                        runtime("com.hivemq:hivemq-enterprise:$hivemqVersion").tag("latest")
                    } else {
                        // the image definition is registered further below, so it is looked up lazily
                        runtime(provider { oci.imageDefinitions["customPlatformImage"].dependency.get() }) //
                            .name("hivemq/hivemq4").tag("latest")
                    }
                    runtime("com.hivemq:hivemq-enterprise-k8s:4.47.1").tag("k8s-latest")
                    runtime(ociImages.hivemq.operator.oci).tag("latest")
                    runtime(ociImages.init.dns.wait.oci).tag("latest")
                    runtime(ociImages.busybox.oci).name("busybox").tag("latest")
                    runtime(ociImages.nginx.oci).name("nginx").tag("latest")
                    runtime(ociImages.selenium.standalone.firefox.oci).tag("latest")
                }
                val linuxAmd64 = platformSelector(platform("linux", "amd64"))
                val linuxArm64v8 = platformSelector(platform("linux", "arm64", "v8"))
                platformSelector = if (System.getenv("CI_RUN") != null //
                    || System.getProperty("os.arch", "").equals("amd64")
                ) linuxAmd64 else linuxAmd64.and(linuxArm64v8)
            }
        }
    }
}

tasks.register("integrationTestPrepare") {
    dependsOn(provider {
        tasks.named("integrationTest").get().taskDependencies.getDependencies(null)
    })
}

/* ******************** OCI images ******************** */

val downloadPlatformDistribution by tasks.registering(Download::class) {
    group = "distribution"
    description = "Downloads the latest HiveMQ Platform distribution"
    src("https://www.hivemq.com/releases/hivemq-latest.zip")
    dest(layout.buildDirectory.file("platform/hivemq-latest.zip"))
    onlyIfModified(true)
    retries(3)
}

oci {
    registries {
        dockerHub {
            optionalCredentials()
        }
        gitHubContainerRegistry {
            exclusiveContent {
                includeModule("hivemq", "helm-test-image")
            }
        }
    }
    imageMapping {
        mapModule("com.hivemq", "hivemq-enterprise") {
            toImage("hivemq/hivemq4").withTag(version)
        }
        mapModule("com.hivemq", "hivemq-enterprise-k8s") {
            toImage("hivemq/hivemq4").withTag(version.prefix("k8s-"))
        }
        jreBaseImages.forEach { (variant, jreBaseImage) ->
            mapModule(jreBaseImageGroup, variant) {
                toImage(jreBaseImage.repository).withTag(version)
            }
        }
    }
    imageDefinitions {
        register("main") {
            imageTag = provider { project.version.toString().lowercase() }
            allPlatforms {
                dependencies {
                    runtime("rancher:k3s:$k3sTag")
                    runtime(ociImages.helm.oci)
                }
            }
        }
        if (customPlatformImageVariant != null) {
            val jreBaseImage = jreBaseImages[customPlatformImageVariant]
                ?: throw GradleException(
                    "Unknown custom platform image variant '$customPlatformImageVariant', " +
                            "expected one of ${jreBaseImages.keys.sorted()}"
                )
            val jreBaseImageReference = jreBaseImage.digest?.replace("sha256:", "sha256!") ?: jreBaseImage.tag
            register("customPlatformImage") {
                imageName = "hivemq/hivemq4"
                imageTag = "latest"
                allPlatforms {
                    dependencies {
                        runtime("$jreBaseImageGroup:$customPlatformImageVariant:$jreBaseImageReference")
                    }
                    config {
                        user = "10000"
                        workingDirectory = "/opt/hivemq"
                        ports = setOf(
                            "1883", // MQTT
                            "8000", // cluster transport
                            "8080", // Control Center HTTP
                        )
                        environment = mapOf(
                            // the user ID that runs the container has no entry in /etc/passwd, so HOME has to be set
                            // explicitly, otherwise it defaults to "/"
                            "HOME" to "/opt/hivemq",
                            "JAVA_OPTS" to "-XX:+UnlockExperimentalVMOptions -XX:+UseNUMA",
                            "LANG" to "en_US.UTF-8",
                        )
                        // no entry point, the HiveMQ Platform Operator sets the container command itself
                    }
                    layer("hivemq") {
                        contents {
                            // the distribution is added with the permissions of the official image, as the container
                            // runs as user 10000 in group 0 and has to write to these directories
                            permissions("opt/hivemq/", 0b111_111_101)
                            permissions("opt/hivemq/**/*.sh", 0b111_101_101)
                            permissions("opt/hivemq/bin/init-script/hivemq*", 0b111_101_101)
                            permissions("opt/hivemq/audit/", 0b111_111_101)
                            permissions("opt/hivemq/backup/", 0b111_111_101)
                            permissions("opt/hivemq/conf/", 0b111_111_101)
                            permissions("opt/hivemq/conf/*.xml", 0b110_110_100)
                            permissions("opt/hivemq/data/", 0b111_111_101)
                            permissions("opt/hivemq/extensions/", 0b111_111_101)
                            permissions("opt/hivemq/extensions/*/", 0b111_111_101)
                            permissions("opt/hivemq/extensions/*/DISABLED", 0b110_110_100)
                            permissions("opt/hivemq/extensions/*/hivemq-extension.xml", 0b110_110_100)
                            permissions("opt/hivemq/license/", 0b111_111_101)
                            permissions("opt/hivemq/log/", 0b111_111_101)
                            into("opt") {
                                from(zipTree(downloadPlatformDistribution.map { it.outputFiles.first() })) {
                                    // the tools are not used by any test
                                    filter { exclude("*/tools/**") }
                                    move("", "hivemq-.*", "hivemq")
                                }
                            }
                        }
                    }
                }
                specificPlatform(platform("linux", "amd64"))
                specificPlatform(platform("linux", "arm64", "v8"))
            }
        }
    }
}

val pushAllImagesForTesting by tasks.registering(oci.pushImageTaskClass) {
    val imageDeps = oci.imageDependencies.create("imagesForTesting")
    imageDeps.runtime("com.hivemq:hivemq-platform-operator").tag("snapshot")
    imageDeps.runtime("com.hivemq:hivemq-platform-operator-init").tag("snapshot")
    imageDeps.runtime("hivemq:hivemq4:$hivemqVersion").tag("latest")
    from(imageDeps)
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
    val k8sVersionType = System.getenv("K8S_VERSION_TYPE") ?: "LATEST"
    val tag = if (k8sVersionType == "MINIMUM") ociImages.k3s.minimum.tag else ociImages.k3s.latest.tag
    println("Resolving test OCI image k3s:$tag ($k8sVersionType)")
    return tag
}
