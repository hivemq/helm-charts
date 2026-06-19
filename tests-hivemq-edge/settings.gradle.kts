plugins {
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.6.0"
    id("com.gradle.develocity") version "4.4.3"
}

rootProject.name = "tests-hivemq-edge"

develocity {
    server = System.getenv("DEVELOCITY_SERVER_URL")
    buildScan {
        publishing.onlyIf { System.getenv("CI_RUN") == "true" }
        uploadInBackground = false
    }
}
