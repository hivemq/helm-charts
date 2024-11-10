plugins {
    application
}

group = "com.hivemq"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.hivemq.release.GitHubReleaseNotesUpdater"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.java.semver)
    implementation(libs.jcommander)
}
