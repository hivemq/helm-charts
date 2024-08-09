package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class JUnitUtil {

    public static void main(final @NotNull String @NotNull [] args) {
        final var testPackage = args.length == 4 ? args[0] : "com.hivemq.helmcharts";
        final var includeTags = args.length == 4 ? args[1] : "";
        final var excludeTags = args.length == 4 ? args[2] : "";
        final var searchIntegrationTests = args.length == 4 && Boolean.parseBoolean(args[3]);

        System.out.printf("Searching for tests in package '%s' (includeTags: %s) (excludeTags: %s)%n",
                testPackage,
                includeTags.isBlank() ? "none" : includeTags,
                excludeTags.isBlank() ? "none" : excludeTags);

        var requestBuilder = LauncherDiscoveryRequestBuilder.request() //
                .selectors(DiscoverySelectors.selectPackage(testPackage));
        if (!includeTags.isBlank()) {
            final var tags = Arrays.stream(includeTags.split(",")).toList();
            requestBuilder = requestBuilder.filters(TagFilter.includeTags(tags));
        }
        if (!excludeTags.isBlank()) {
            final var tags = Arrays.stream(excludeTags.split(",")).toList();
            requestBuilder = requestBuilder.filters(TagFilter.excludeTags(tags));
        }
        final var request = requestBuilder.build();

        final var launcher = LauncherFactory.create();
        final var testPlan = launcher.discover(request);

        final var listener = new TestDiscoveryListener();
        listener.testPlanExecutionStarted(testPlan);

        final var tests = listener.getTests()
                .stream()
                .filter(testIdentifier -> !searchIntegrationTests || testIdentifier.getDisplayName().endsWith("IT"))
                .toList();
        System.out.printf("Found %s %s:%n", tests.size(), searchIntegrationTests ? "integration tests": "tests");
        tests.forEach(testIdentifier -> {
            if (testIdentifier.getSource().isEmpty()) {
                throw new IllegalStateException("Test '" + testIdentifier.getDisplayName() + "' has no source");
            }
            final var testSource = testIdentifier.getSource().get();
            if (!(testSource instanceof final ClassSource classSource)) {
                throw new IllegalStateException("Test '" + testIdentifier.getDisplayName() + "' is no ClassSource");
            }
            System.out.println("  " + classSource.getClassName());
        });
    }

    private static class TestDiscoveryListener implements TestExecutionListener {

        private final @NotNull List<TestIdentifier> tests = new ArrayList<>();

        @Override
        public void testPlanExecutionStarted(final @NotNull TestPlan testPlan) {
            collectTests(testPlan, testPlan.getRoots());
        }

        private void collectTests(final @NotNull TestPlan testPlan, final @NotNull Set<TestIdentifier> identifiers) {
            identifiers.forEach(identifier -> {
                if (identifier.isContainer() &&
                        identifier.getSource().isPresent() &&
                        identifier.getSource().get() instanceof ClassSource) {
                    tests.add(identifier);
                }
                collectTests(testPlan, testPlan.getChildren(identifier));
            });
        }

        private @NotNull List<TestIdentifier> getTests() {
            return tests;
        }
    }
}
