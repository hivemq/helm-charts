import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
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

val hivemqEdgeVersion = libs.versions.hivemq.edge.get()
val k3sTag = resolveK3sTag()

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

                // testcontainers
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.testcontainers.k3s)

                // testing
                implementation(libs.assertj)
                implementation(libs.awaitility)

                // misc
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
                    systemProperty("k3s.version.type", environment["K8S_VERSION_TYPE"] ?: "LATEST")
                    systemProperty("hivemq.edge.tag", libs.versions.hivemq.edge.get())
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
                    runtime("com.hivemq:hivemq-edge:$hivemqEdgeVersion").tag("latest")
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
        registry("ecrPublic") {
            url = uri("https://public.ecr.aws")
            exclusiveContent { includeGroup("hivemq.library") }
        }
    }
    imageMapping {
        mapGroup("hivemq.library") {
            toImage(nameSpec("hivemq/library/") + name)
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
    }
}

fun resolveK3sTag(): String {
    val k8sVersionType = System.getenv("K8S_VERSION_TYPE") ?: "LATEST"
    val tag = if (k8sVersionType == "MINIMUM") ociImages.k3s.minimum.tag else ociImages.k3s.latest.tag
    println("Resolving test OCI image k3s:$tag ($k8sVersionType)")
    return tag
}
