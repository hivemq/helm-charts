plugins {
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.6.0"
    id("com.gradle.develocity") version "4.4.3"
}

rootProject.name = "tests-hivemq-platform-operator"

develocity {
    server = System.getenv("DEVELOCITY_SERVER_URL")
    buildScan {
        publishing.onlyIf { System.getenv("CI_RUN") == "true" }
        uploadInBackground = false
    }
}

if (file("../../hivemq-platform-operator/hivemq-platform-operator-init").exists()) {
    includeBuild("../../hivemq-platform-operator/hivemq-platform-operator-init")
}
if (file("../../hivemq-platform-operator/").exists()) {
    includeBuild("../../hivemq-platform-operator/")
}
