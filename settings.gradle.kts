rootProject.name = "helm-charts"

if (file("../hivemq/").exists()) {
    includeBuild("../hivemq/")
}
if (file("../hivemq-operator/").exists()) {
    includeBuild("../hivemq-operator/")
}
