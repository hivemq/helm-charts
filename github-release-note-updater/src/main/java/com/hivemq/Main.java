package com.hivemq;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zafarkhaja.semver.Version;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static final @NotNull String PLATFORM_RELEASE_NOTE_TEMPLATE = """
            %s
            
            [Updated to HiveMQ Platform %s](%s)
            """;
    private static final @NotNull String OPERATOR_RELEASE_NOTE_TEMPLATE = """
            %s
            
            [Updated to HiveMQ Platform Operator %s](%s)
            """;

    public static void main(final @NotNull String @NotNull [] args) throws Exception {
        final var arguments = new Arguments();
        final var jCommander = JCommander.newBuilder().addObject(arguments).build();
        jCommander.parse(args);
        if (arguments.help) {
            jCommander.usage();
            System.exit(0);
        }

        final var chartsPath = Path.of(arguments.path, "charts.json");
        final var releasesPath = Path.of(arguments.path, "releases.json");
        if (Files.notExists(chartsPath)) {
            System.err.println("Charts path does not exist.");
            System.exit(1);
        }
        if (Files.notExists(releasesPath)) {
            System.err.println("Releases path does not exist.");
            System.exit(1);
        }

        final var objectMapper = new ObjectMapper();
        final var charts = Arrays.stream(objectMapper.readValue(Files.readString(chartsPath), Chart[].class)).toList();
        final var releases =
                Arrays.stream(objectMapper.readValue(Files.readString(releasesPath), Release[].class)).toList();

        // sort the released Helm charts
        final var platformOperatorCharts =
                charts.stream().filter(chart -> chart.name().equals("hivemq-platform-operator")).sorted().toList();
        final var platformCharts =
                charts.stream().filter(chart -> chart.name().equals("hivemq-platform")).sorted().toList();
        final var legacyOperatorCharts =
                charts.stream().filter(chart -> chart.name().equals("hivemq-operator")).sorted().toList();
        final var swarmCharts = charts.stream().filter(chart -> chart.name().equals("hivemq-swarm")).sorted().toList();

        // prepare the release notes
        final var releaseNotes = new HashMap<String, String>();
        setPlatformReleaseNotes(releaseNotes, releases, platformOperatorCharts, platformCharts);
        setPlatformReleaseNotes(releaseNotes, releases, platformOperatorCharts, legacyOperatorCharts);
        setPlatformReleaseNotes(releaseNotes, releases, platformOperatorCharts, swarmCharts);
        platformOperatorCharts.forEach(chart -> {
            final var releaseTag = String.format("%s-%s", chart.name(), chart.version());
            final var releaseNote = String.format(OPERATOR_RELEASE_NOTE_TEMPLATE,
                    chart.description(),
                    chart.appVersion(),
                    setPlatformOperatorReleaseUrl(chart.appVersion()));
            releaseNotes.put(releaseTag, releaseNote);
        });

        // update the GitHub release notes
        try (var executorService = Executors.newSingleThreadExecutor()) {
            for (final var release : releases) {
                final var releaseTag = release.tagName();
                final var releaseNote = releaseNotes.get(releaseTag);
                if (releaseNote == null) {
                    System.out.println("Skipping release " + releaseTag);
                    continue;
                }
                final var exitCode = execute(executorService,
                        arguments.gitHubCliPath,
                        "release",
                        "edit",
                        releaseTag,
                        "--repo",
                        "hivemq/helm-charts",
                        "--notes",
                        releaseNote);
                System.out.println(releaseTag + " -> " + (exitCode == 0 ? "SUCCESS" : "FAILURE"));
            }
        }
    }

    private static void setPlatformReleaseNotes(
            final @NotNull Map<String, String> releaseNotes,
            final @NotNull List<Release> releases,
            final @NotNull List<Chart> platformOperatorCharts,
            final @NotNull List<Chart> charts) {
        for (int i = 0; i < charts.size(); i++) {
            final var chart = charts.get(i);
            final var previousChart = i == 0 ? null : charts.get(i - 1);
            final var wasChartUpdated = previousChart == null || !previousChart.appVersion().equals(chart.appVersion());
            final var releaseTag = String.format("%s-%s", chart.name(), chart.version());
            final var operatorReleaseOptional = getMatchingOperatorRelease(releases, releaseTag);
            if (wasChartUpdated || operatorReleaseOptional.isEmpty()) {
                // this release was triggered by a HiveMQ Platform release
                final var releaseNote = String.format(PLATFORM_RELEASE_NOTE_TEMPLATE,
                        chart.description(),
                        chart.appVersion(),
                        getPlatformReleaseUrl(chart.appVersion()));
                releaseNotes.put(releaseTag, releaseNote);
            } else {
                // this release was triggered by a HiveMQ Platform Operator release
                final var operatorRelease = operatorReleaseOptional.get();
                final var operatorChartVersion = Version.parse(operatorRelease.tagName()
                        .substring(operatorRelease.tagName().lastIndexOf('-') + 1));
                final var operatorChart = platformOperatorCharts.stream()
                        .filter(c -> c.version().equals(operatorChartVersion))
                        .findFirst()
                        .orElseThrow();
                final var releaseNote = String.format(OPERATOR_RELEASE_NOTE_TEMPLATE,
                        chart.description(),
                        operatorChart.appVersion(),
                        setPlatformOperatorReleaseUrl(operatorChart.appVersion()));
                releaseNotes.put(releaseTag, releaseNote);
            }
        }
    }

    private static @NotNull Optional<Release> getMatchingOperatorRelease(
            final @NotNull List<Release> releases, //
            final @NotNull String releaseTag) {
        return releases.stream()
                .filter(release -> release.tagName().equals(releaseTag))
                .findFirst()
                .flatMap(value -> releases.stream()
                        .filter(release -> release.tagName().startsWith("hivemq-platform-operator-"))
                        .filter(release -> release.publishedAt().equals(value.publishedAt()))
                        .findFirst());
    }

    private static @NotNull String getPlatformReleaseUrl(final @NotNull Version version) {
        if (version.patchVersion() != 0) {
            return String.format("https://www.hivemq.com/changelog/hivemq-%s-%s-%s-released/",
                    version.majorVersion(),
                    version.minorVersion(),
                    version.patchVersion());
        }
        return String.format("https://www.hivemq.com/changelog/whats-new-in-hivemq-%s-%s/",
                version.majorVersion(),
                version.minorVersion());
    }

    private static @NotNull String setPlatformOperatorReleaseUrl(final @NotNull Version version) {
        return String.format("https://www.hivemq.com/changelog/hivemq-platform-operator-%s-%s-%s-release/",
                version.majorVersion(),
                version.minorVersion(),
                version.patchVersion());
    }

    private static int execute(final @NotNull ExecutorService executorService, final @NotNull String... command)
            throws Exception {
        final var process = new ProcessBuilder().command(command).start();
        final var inputStreamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
        final var errorStreamGobbler = new StreamGobbler(process.getErrorStream(), System.err::println);
        executorService.submit(inputStreamGobbler);
        executorService.submit(errorStreamGobbler);
        return process.waitFor();
    }
}
