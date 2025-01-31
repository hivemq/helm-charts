rootProject.name = "tests-hivemq-platform-operator"

if (file("../../hivemq-platform-operator/hivemq-platform-operator-init").exists()) {
    includeBuild("../../hivemq-platform-operator/hivemq-platform-operator-init")
}
if (file("../../hivemq-platform-operator/").exists()) {
    includeBuild("../../hivemq-platform-operator/")
}
