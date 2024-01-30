rootProject.name = "tests-hivemq-operator"

if (file("../../hivemq-operator/").exists()) {
    includeBuild("../../hivemq-operator/")
}
