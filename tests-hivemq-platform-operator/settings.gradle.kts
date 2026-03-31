plugins {
    id("com.hivemq.tools.oci-version-catalog") version "0.1.0"
}

rootProject.name = "tests-hivemq-platform-operator"

if (file("../../hivemq-platform-operator/hivemq-platform-operator-init").exists()) {
    includeBuild("../../hivemq-platform-operator/hivemq-platform-operator-init")
}
if (file("../../hivemq-platform-operator/").exists()) {
    includeBuild("../../hivemq-platform-operator/")
}
