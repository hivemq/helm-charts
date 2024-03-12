/* ******************** update versions ******************** */

val updateOperatorChartVersion by tasks.registering(Exec::class) {
    group = "version"
    description =
        "Bumps Operator Legacy Chart and Platform versions. " +
                "\n\tUsage: ./gradlew updateOperatorChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z" +
                "\n\t\t- 'chartVersion': Operator Legacy chart version. Optional, if not present, it will automatically be bumped to the next patch version." +
                "\n\t\t- 'appVersion': Platform version. Optional."
    doFirst {
        updateChartAndValueFilesWithVersion(
            arrayOf(
                "charts/hivemq-operator/Chart.yaml",
                "charts/hivemq-operator/values.yaml"
            ), """(image:\s+[^:]+:\w+-)(\S+)"""
        )
    }
    dependsOn(gradle.includedBuild("tests-hivemq-operator").task(":updatePlatformVersion"))
    workingDir(layout.projectDirectory)
    commandLine("sh", "./manifests/hivemq-operator/manifests.sh")
}

val updateSwarmChartVersion by tasks.registering(Exec::class) {
    group = "version"
    description =
        "Bumps Swarm Chart and Platform versions. " +
                "\n\tUsage: ./gradlew updateSwarmChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z" +
                "\n\t\t- 'chartVersion': Swarm chart version. Optional, if not present, it will automatically be bumped to the next patch version." +
                "\n\t\t- 'appVersion': Platform version. Optional."
    doFirst {
        updateChartAndValueFilesWithVersion(
            arrayOf("charts/hivemq-swarm/Chart.yaml", "charts/hivemq-swarm/values.yaml"),
            """(tag:\s*)(\S+)"""
        )
    }
    workingDir(layout.projectDirectory)
    commandLine("sh", "./manifests/hivemq-swarm/manifests.sh")
}

val updatePlatformOperatorChartVersion by tasks.registering(Exec::class) {
    group = "version"
    description =
        "Bumps Platform Operator Chart and Operator Platform versions. " +
                "\n\tUsage: ./gradlew updatePlatformOperatorChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z" +
                "\n\t\t- 'chartVersion': Platform Operator chart version. Optional, if not present, it will automatically be bumped to the next patch version." +
                "\n\t\t- 'appVersion': Platform Operator version. Optional."
    doFirst {
        updateChartAndValueFilesWithVersion(
            arrayOf(
                "charts/hivemq-platform-operator/Chart.yaml",
                "charts/hivemq-platform-operator/values.yaml"
            ), """(tag:\s*)(\S+)"""
        )
    }
    workingDir(layout.projectDirectory)
    commandLine("sh", "./manifests/hivemq-platform-operator/manifests.sh")
}

val updatePlatformChartVersion by tasks.registering(Exec::class) {
    group = "version"
    description =
        "Bumps Platform Chart and Platform versions. " +
                "\n\tUsage: ./gradlew updatePlatformChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z" +
                "\n\t\t- 'chartVersion': Platform chart version. Optional, if not present, it will automatically be bumped to the next patch version." +
                "\n\t\t- 'appVersion': Platform release version. Optional."
    doFirst {
        updateChartAndValueFilesWithVersion(
            arrayOf(
                "charts/hivemq-platform/Chart.yaml",
                "charts/hivemq-platform/values.yaml"
            ), """(tag:\s*)(\S+)"""
        )
    }
    dependsOn(gradle.includedBuild("tests-hivemq-platform-operator").task(":updatePlatformVersion"))
    workingDir(layout.projectDirectory)
    commandLine("sh", "./manifests/hivemq-platform/manifests.sh")
}

var checkAppVersion = false
var checkAppVersionUsage = ""

val updateAllPlatformChartVersions by tasks.registering {
    val usage = "Usage: ./gradlew updateAllPlatformChartVersions -PappVersion=x.y.z" +
            "\n\t\t- 'appVersion': Platform release version. Mandatory."
    group = "version"
    description = "Bumps all Platform Charts and Platform versions except HiveMQ Platform Operator chart.\n\t$usage"
    checkAppVersion = true
    checkAppVersionUsage = usage
    dependsOn(updateOperatorChartVersion)
    dependsOn(updateSwarmChartVersion)
    dependsOn(updatePlatformChartVersion)
}

fun updateChartAndValueFilesWithVersion(versionFilesToUpdate: Array<String>, valuesRegex: String) {
    var chartVersion = project.properties["chartVersion"]
    val appVersion = project.properties["appVersion"]
    if (checkAppVersion && appVersion == null) {
        error("`appVersion` must be set\n\n$checkAppVersionUsage")
    }
    val filesToUpdate = files(versionFilesToUpdate)
    filesToUpdate.filter { file -> file.name == "Chart.yaml" }.forEach {
        var replacedTextAppVersion = it.readText()
        if (appVersion != null) {
            replacedTextAppVersion =
                replacedTextAppVersion.replace("""(?m)^appVersion:\s*\S+$""".toRegex(), "appVersion: $appVersion")
        }
        val currentChartVersionMatch = """(?m)^version:\s*(\S+)$""".toRegex().find(replacedTextAppVersion)
        val currentChartVersion = currentChartVersionMatch?.groupValues?.get(1)
        if (chartVersion == null) {
            // Bump the last part of the chart version
            val updatedVersion = currentChartVersion?.let { version ->
                val parts = version.split('.')
                if (parts.size == 3) {
                    val incrementedVersion = (parts[2].toIntOrNull() ?: 0) + 1
                    "${parts[0]}.${parts[1]}.$incrementedVersion"
                } else {
                    version
                }
            }
            chartVersion = updatedVersion
        }
        val replacedChartVersion =
            replacedTextAppVersion.replace("""(?m)^version:\s*\S+$""".toRegex(), "version: $chartVersion")
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
