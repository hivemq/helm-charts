plugins {
    java
}

group = "com.hivemq.helmcharts"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

repositories {
    mavenCentral()
}

/* ******************** update versions ******************** */

val updateOperatorChartVersion by tasks.registering {
    group = "version"
    project.ext.set("versionFilesToUpdate", arrayOf("charts/hivemq-operator/Chart.yaml", "charts/hivemq-operator/values.yaml"))
    project.ext.set("valuesRegex", """(image:\s+[^:]+:\w+-)(\S+)""")
    dependsOn(updateChartAndValueFilesWithVersion)
    dependsOn(gradle.includedBuild("tests-hivemq-operator").task(":updatePlatformVersion"))
    doLast {
        exec {
            workingDir(projectDir)
            commandLine("sh", "./manifests/hivemq-operator/manifests.sh")
        }
    }
}

val updateSwarmChartVersion by tasks.registering {
    group = "version"
    project.ext.set("versionFilesToUpdate", arrayOf("charts/hivemq-swarm/Chart.yaml", "charts/hivemq-swarm/values.yaml"))
    project.ext.set("valuesRegex", """(tag:\s*)(\S+)""")
    dependsOn(updateChartAndValueFilesWithVersion)
    doLast {
        exec {
            workingDir(projectDir)
            commandLine("sh", "./manifests/hivemq-swarm/manifests.sh")
        }
    }
}

val updatePlatformOperatorChartVersion by tasks.registering {
    group = "version"
    project.ext.set("versionFilesToUpdate", arrayOf("charts/hivemq-platform-operator/Chart.yaml", "charts/hivemq-platform-operator/values.yaml"))
    project.ext.set("valuesRegex", """(tag:\s*)(\S+)""")
    dependsOn(updateChartAndValueFilesWithVersion)
    doLast {
        exec {
            workingDir(projectDir)
            commandLine("sh", "./manifests/hivemq-platform-operator/manifests.sh")
        }
    }
}

val updatePlatformChartVersion by tasks.registering {
    group = "version"
    project.ext.set("versionFilesToUpdate", arrayOf("charts/hivemq-platform/Chart.yaml", "charts/hivemq-platform/values.yaml"))
    project.ext.set("valuesRegex", """(tag:\s*)(\S+)""")
    dependsOn(updateChartAndValueFilesWithVersion)
    dependsOn(gradle.includedBuild("tests-hivemq-platform-operator").task(":updatePlatformVersion"))
    doLast {
        exec {
            workingDir(projectDir)
            commandLine("sh", "./manifests/hivemq-platform/manifests.sh")
        }
    }
}

val updateChartAndValueFilesWithVersion by tasks.registering {
    group = "version"
    doLast {
        val filesToUpdate = files(project.properties["versionFilesToUpdate"])
        val chartVersion = project.properties["chartVersion"]
        val appVersion = project.properties["appVersion"]
        val valuesRegex = project.properties["valuesRegex"] as String
        if (chartVersion == null) {
            error("`chartVersion` must be set")
        }
        filesToUpdate.filter { file -> file.name == "Chart.yaml" }.forEach {
            var replacedTextAppVersion = it.readText()
            if (appVersion != null) {
                replacedTextAppVersion = replacedTextAppVersion.replace("""(?m)^appVersion:\s*\S+$""".toRegex(), "appVersion: $appVersion")
            }
            val replacedChartVersion = replacedTextAppVersion.replace("""(?m)^version:\s*\S+$""".toRegex(), "version: $chartVersion")
            it.writeText(replacedChartVersion)
        }
        if (appVersion != null) {
            filesToUpdate.filter { file -> file.name == "values.yaml" }.forEach {
                val text = it.readText()
                val replacedText = text.replace(valuesRegex.toRegex()) { matchResult ->
                    "${matchResult.groupValues[1]}${appVersion}"
                }
                it.writeText(replacedText)
            }
        }
    }
}
