/* ******************** update versions ******************** */

val updateOperatorChartVersion by tasks.registering(Exec::class) {
    group = "version"
    description =
        "Bumps Operator Legacy Chart and Platform versions." +
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
        "Bumps Swarm Chart and Platform versions." +
                "\n\tUsage: ./gradlew updateSwarmChartVersion -PchartVersion=a.b.c -PappVersion=x.y.z" +
                "\n\t\t- 'chartVersion': Swarm chart version. Optional, if not present, it will automatically be bumped to the next patch version." +
                "\n\t\t- 'appVersion': Platform version. Optional."
    doFirst {
        updateChartAndValueFilesWithVersion(
            arrayOf(
                "charts/hivemq-swarm/Chart.yaml",
                "charts/hivemq-swarm/values.yaml"
            ), """(tag:\s*)(\S+)"""
        )
    }
    workingDir(layout.projectDirectory)
    commandLine("sh", "./manifests/hivemq-swarm/manifests.sh")
}

val updatePlatformOperatorChartVersion by tasks.registering(Exec::class) {
    group = "version"
    description =
        "Bumps Platform Operator Chart and Operator Platform versions." +
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
        "Bumps Platform Chart and Platform versions." +
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

val updateAllManifestFiles by tasks.registering {
    group = "version"
    description = "Updates all manifest files."
    doLast {
        exec {
            workingDir(layout.projectDirectory)
            commandLine("sh", "./manifests/hivemq-operator/manifests.sh")
        }
        exec {
            workingDir(layout.projectDirectory)
            commandLine("sh", "./manifests/hivemq-platform/manifests.sh")
        }
        exec {
            workingDir(layout.projectDirectory)
            commandLine("sh", "./manifests/hivemq-platform-operator/manifests.sh")
        }
        exec {
            workingDir(layout.projectDirectory)
            commandLine("sh", "./manifests/hivemq-swarm/manifests.sh")
        }
    }
}

val test by tasks.registering {
    group = "test"
    description = "Executes all Helm unit tests."
    doLast {
        exec {
            workingDir(layout.projectDirectory)
            commandLine("helm", "unittest", "./charts/hivemq-operator", "-f", "./tests/**/*_test.yaml")
        }
        exec {
            workingDir(layout.projectDirectory)
            commandLine("helm", "unittest", "./charts/hivemq-platform", "-f", "./tests/**/*_test.yaml")
        }
        exec {
            workingDir(layout.projectDirectory)
            commandLine("helm", "unittest", "./charts/hivemq-platform-operator", "-f", "./tests/**/*_test.yaml")
        }
        exec {
            workingDir(layout.projectDirectory)
            commandLine("helm", "unittest", "./charts/hivemq-swarm", "-f", "./tests/**/*_test.yaml")
        }
    }
}

fun updateChartAndValueFilesWithVersion(versionFilesToUpdate: Array<String>, valuesRegex: String) {
    val appVersion = project.properties["appVersion"]
    if (checkAppVersion && appVersion == null) {
        error("`appVersion` must be set\n\n$checkAppVersionUsage")
    }
    val filesToUpdate = files(versionFilesToUpdate)
    filesToUpdate.filter { file -> file.name == "Chart.yaml" }.forEach { file ->
        var text = file.readText()
        val chartVersion = project.properties["chartVersion"] ?: run {
            // bump the last part of the current chart version
            val chartVersionMatch = """(?m)^version:\s*(\S+)$""".toRegex().find(text)
            val currentVersion = chartVersionMatch?.groupValues?.get(1)
                ?: error("Failed to determine current chart version in $file, set `chartVersion` manually.")
            val versionParts = currentVersion.split('.').takeIf { it.size == 3 }
                ?: error("Failed to determine patch version of $currentVersion in $file, set `chartVersion` manually.")
            val incrementedVersion = (versionParts[2].toIntOrNull() ?: 0) + 1
            "${versionParts[0]}.${versionParts[1]}.$incrementedVersion"
        }
        text = text.replace("""(?m)^version:\s*\S+$""".toRegex(), "version: $chartVersion").also {
            require(it != text) { error("Failed to replace version with $chartVersion in $file") }
        }
        if (appVersion != null) {
            text = text.replace("""(?m)^appVersion:\s*\S+$""".toRegex(), "appVersion: $appVersion").also {
                require(it != text) { error("Failed to replace appVersion with $appVersion in $file") }
            }
        }
        file.writeText(text)
    }
    if (appVersion != null) {
        filesToUpdate.filter { file -> file.name == "values.yaml" }.forEach { file ->
            val text = file.readText()
            file.writeText(text.replace(valuesRegex.toRegex()) {
                "${it.groupValues[1]}${appVersion}"
            })
        }
    }
}
