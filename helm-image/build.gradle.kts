import de.undercouch.gradle.tasks.download.Download
import io.github.sgtsilvio.gradle.oci.OciCopySpec
import io.github.sgtsilvio.gradle.oci.image.PushOciImageTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.security.MessageDigest

plugins {
    java
    alias(libs.plugins.download)
    alias(libs.plugins.oci)
}

val helmVersion = providers.fileContents(layout.projectDirectory.dir("..").file(".helm-version")).asText.get().trim()

group = "com.hivemq.helmcharts"
version = helmVersion.removePrefix("v")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val imageAnnotations = mapOf(
    "org.opencontainers.image.title" to "Helm",
    "org.opencontainers.image.description" to
            "Helm binary for the HiveMQ helm-charts integration tests. Not a supported HiveMQ artifact.",
    "org.opencontainers.image.source" to "https://github.com/hivemq/helm-charts",
    "org.opencontainers.image.url" to "https://helm.sh",
    "org.opencontainers.image.version" to version.toString(),
    "org.opencontainers.image.licenses" to "Apache-2.0",
)

val extractHelmLinuxAmd64 = registerHelmDistributionTasks("linux-amd64")
val extractHelmLinuxArm64 = registerHelmDistributionTasks("linux-arm64")

@Suppress("UnstableApiUsage")
testing {
    suites {
        @Suppress("unused")
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                runtimeOnly(libs.junit.platform.launcher)
                implementation(libs.assertj)
                implementation(libs.gradleOci.junitJupiter)
                implementation(libs.testcontainers)
                runtimeOnly(libs.logback.classic)
            }
            targets.configureEach {
                testTask {
                    systemProperty("helm.version", helmVersion)
                    systemProperty("helm.image.tag", version.toString())
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
                }
            }
            oci.of(this) {
                imageDependencies {
                    runtime(project())
                }
                val linuxAmd64 = platformSelector(platform("linux", "amd64"))
                val linuxArm64v8 = platformSelector(platform("linux", "arm64", "v8"))
                platformSelector = if (System.getenv("CI_RUN") != null //
                    || System.getProperty("os.arch", "").equals("amd64")
                ) linuxAmd64 else linuxArm64v8
            }
        }
    }
}

/* ******************** OCI images ******************** */

val ghcr = oci.registries.gitHubContainerRegistry {
    optionalCredentials()
}

oci {
    imageDefinitions {
        register("main") {
            imageName = "hivemq/helm-test-image"
            imageTag = version.toString()
            indexAnnotations = imageAnnotations
            specificPlatform(platform("linux", "amd64")) {
                config {
                    manifestAnnotations = imageAnnotations
                }
                layer("helm") {
                    contents {
                        helmBinary(extractHelmLinuxAmd64)
                    }
                }
            }
            specificPlatform(platform("linux", "arm64", "v8")) {
                config {
                    manifestAnnotations = imageAnnotations
                }
                layer("helm") {
                    contents {
                        helmBinary(extractHelmLinuxArm64)
                    }
                }
            }
        }
    }
}

tasks.named<PushOciImageTask>("pushOciImage") {
    registry {
        from(ghcr)
    }
}

fun registerHelmDistributionTasks(platform: String): TaskProvider<Sync> {
    val taskSuffix = platform.split('-').joinToString("") { it.replaceFirstChar(Char::titlecase) }
    val archiveName = "helm-$helmVersion-$platform.tar.gz"
    val downloadDirectory = layout.buildDirectory.dir("helm/download/$platform")
    val archiveFile = downloadDirectory.map { it.file(archiveName) }
    val checksumFile = downloadDirectory.map { it.file("$archiveName.sha256sum") }

    val download = tasks.register<Download>("downloadHelm$taskSuffix") {
        group = "distribution"
        description = "Downloads the Helm $helmVersion distribution for $platform."
        src(
            listOf(
                "https://get.helm.sh/$archiveName",
                "https://get.helm.sh/$archiveName.sha256sum",
            )
        )
        dest(downloadDirectory)
        overwrite(false)
        retries(3)
    }

    return tasks.register<Sync>("extractHelm$taskSuffix") {
        group = "distribution"
        description = "Verifies and extracts the Helm $helmVersion binary for $platform."
        dependsOn(download)
        inputs.file(archiveFile)
        inputs.file(checksumFile)
        doFirst {
            verifyChecksum(archiveFile.get().asFile, checksumFile.get().asFile)
        }
        from(tarTree(archiveFile)) {
            include("*/helm")
            eachFile {
                relativePath = RelativePath(true, name)
            }
        }
        includeEmptyDirs = false
        into(layout.buildDirectory.dir("helm/bin/$platform"))
    }
}

fun OciCopySpec.helmBinary(extractTask: TaskProvider<Sync>) {
    from(extractTask) {
        into("usr/local/bin")
    }
    permissions("usr/local/bin/helm", 0b111_101_101)
}

fun verifyChecksum(archive: File, checksum: File) {
    val expected = checksum.readText().trim().substringBefore(' ')
    val actual = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
        .joinToString("") { "%02x".format(it) }
    check(actual == expected) { "SHA-256 mismatch for ${archive.name}, expected $expected but was $actual" }
}
