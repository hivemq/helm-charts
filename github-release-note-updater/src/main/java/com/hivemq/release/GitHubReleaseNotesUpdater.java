package com.hivemq.release;

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
import java.util.function.Function;
import java.util.regex.Pattern;

public class GitHubReleaseNotesUpdater {

    private static final @NotNull String PLATFORM_RELEASE_NOTE_TEMPLATE = """
            %s
            
            [Updated to HiveMQ Platform %s](%s)
            """;
    private static final @NotNull String OPERATOR_RELEASE_NOTE_TEMPLATE = """
            %s
            
            [Updated to HiveMQ Platform Operator %s](%s)
            """;

    private static final @NotNull String PLATFORM_MAINTENANCE_RELEASE_URL =
            "https://www.hivemq.com/changelog/hivemq-%s-%s-%s-released/";
    private static final @NotNull String PLATFORM_FEATURE_RELEASE_URL =
            "https://www.hivemq.com/changelog/whats-new-in-hivemq-%s-%s/";
    private static final @NotNull String OPERATOR_RELEASE_URL =
            "https://www.hivemq.com/changelog/hivemq-platform-operator-%s-%s-%s-release/";

    private static final @NotNull Pattern PLATFORM_RELEASE_PATTERN =
            Pattern.compile("^hivemq-platform-(\\d+\\.\\d+\\.\\d+)$");
    private static final @NotNull Pattern OPERATOR_RELEASE_PATTERN =
            Pattern.compile("^hivemq-platform-operator-(\\d+\\.\\d+\\.\\d+)$");

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
            System.err.printf("Charts path '%s' does not exist%n", chartsPath);
            System.exit(1);
        }
        if (Files.notExists(releasesPath)) {
            System.err.printf("Releases path '%s' does not exist%n", releasesPath);
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
        setReleaseNotes(releaseNotes,
                releases,
                OPERATOR_RELEASE_PATTERN,
                platformCharts,
                PLATFORM_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getPlatformReleaseUrl,
                platformOperatorCharts,
                OPERATOR_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getOperatorReleaseUrl);
        setReleaseNotes(releaseNotes,
                releases,
                OPERATOR_RELEASE_PATTERN,
                legacyOperatorCharts,
                PLATFORM_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getPlatformReleaseUrl,
                platformOperatorCharts,
                OPERATOR_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getOperatorReleaseUrl);
        setReleaseNotes(releaseNotes,
                releases,
                OPERATOR_RELEASE_PATTERN,
                swarmCharts,
                PLATFORM_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getPlatformReleaseUrl,
                platformOperatorCharts,
                OPERATOR_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getOperatorReleaseUrl);
        setReleaseNotes(releaseNotes,
                releases,
                PLATFORM_RELEASE_PATTERN,
                platformOperatorCharts,
                OPERATOR_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getOperatorReleaseUrl,
                platformCharts,
                PLATFORM_RELEASE_NOTE_TEMPLATE,
                GitHubReleaseNotesUpdater::getPlatformReleaseUrl);

        // update the GitHub release notes
        try (var executorService = Executors.newSingleThreadExecutor()) {
            var success = true;
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
                if (exitCode != 0) {
                    success = false;
                }
            }
            if (!success) {
                System.err.println("Not all release notes were updated successfully!");
                System.exit(1);
            }
        }
    }

    private static void setReleaseNotes(
            final @NotNull Map<String, String> releaseNotes,
            final @NotNull List<Release> releases,
            final @NotNull Pattern matchingReleasePattern,
            final @NotNull List<Chart> charts,
            final @NotNull String releaseNoteTemplate,
            final @NotNull Function<Version, String> releaseUrlFunction,
            final @NotNull List<Chart> otherCharts,
            final @NotNull String otherReleaseNoteTemplate,
            final @NotNull Function<Version, String> otherReleaseUrlFunction) {
        for (int i = 0; i < charts.size(); i++) {
            // we get the previous chart and check if the appVersion has changed in the current chart
            // (if so we generate a release note for this chart, otherwise for the matching otherChart)
            final var chart = charts.get(i);
            final var previousChart = i == 0 ? null : charts.get(i - 1);
            final var wasChartUpdated = previousChart == null || !previousChart.appVersion().equals(chart.appVersion());
            final var releaseTag = String.format("%s-%s", chart.name(), chart.version());
            final var otherReleaseOptional = getMatchingRelease(releases, releaseTag, matchingReleasePattern);
            if (wasChartUpdated || otherReleaseOptional.isEmpty()) {
                // this release was triggered by an update of this chart
                final var releaseNote = String.format(releaseNoteTemplate,
                        chart.description(),
                        chart.appVersion(),
                        releaseUrlFunction.apply(chart.appVersion()));
                releaseNotes.put(releaseTag, releaseNote);
            } else {
                // this release was triggered by an update of the other chart
                final var otherRelease = otherReleaseOptional.get();
                final var otherChartVersion =
                        Version.parse(otherRelease.tagName().substring(otherRelease.tagName().lastIndexOf('-') + 1));
                final var otherChart = otherCharts.stream()
                        .filter(c -> c.version().equals(otherChartVersion))
                        .findFirst()
                        .orElseThrow();
                final var releaseNote = String.format(otherReleaseNoteTemplate,
                        chart.description(),
                        otherChart.appVersion(),
                        otherReleaseUrlFunction.apply(otherChart.appVersion()));
                releaseNotes.put(releaseTag, releaseNote);
            }
        }
    }

    private static @NotNull Optional<Release> getMatchingRelease(
            final @NotNull List<Release> releases,
            final @NotNull String releaseTag,
            final @NotNull Pattern matchingReleasePattern) {
        return releases.stream()
                .filter(release -> release.tagName().equals(releaseTag))
                .findFirst()
                .flatMap(value -> releases.stream()
                        .filter(release -> matchingReleasePattern.matcher(release.tagName()).matches())
                        .filter(release -> release.publishedAt().equals(value.publishedAt()))
                        .findFirst());
    }

    private static @NotNull String getPlatformReleaseUrl(final @NotNull Version version) {
        if (version.patchVersion() != 0) {
            return String.format(PLATFORM_MAINTENANCE_RELEASE_URL,
                    version.majorVersion(),
                    version.minorVersion(),
                    version.patchVersion());
        }
        return String.format(PLATFORM_FEATURE_RELEASE_URL, version.majorVersion(), version.minorVersion());
    }

    private static @NotNull String getOperatorReleaseUrl(final @NotNull Version version) {
        return String.format(OPERATOR_RELEASE_URL,
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
