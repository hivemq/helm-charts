package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Filters multiple duplicated log lines of the K3s testcontainer.
 */
class K3sLogConsumer implements Consumer<OutputFrame> {

    private static final @NotNull String LINE_BREAK_AT_END_REGEX = "((\\r?\\n)|(\\r))$";
    private static final @NotNull Pattern K8S_STARTUP_PREFIX =
            Pattern.compile("time=\"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z\" level=(.*) msg=\"(.*)");
    private static final @NotNull Pattern K8S_LOG_PREFIX =
            Pattern.compile("[A-Z0-9]{5} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{6} {6}[0-9]{2} (.*)");

    private final @NotNull Logger logger;

    private @NotNull String prefix = "";

    K3sLogConsumer(final @NotNull Logger logger) {
        this.logger = logger;
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull K3sLogConsumer withPrefix(final @NotNull String prefix) {
        this.prefix = "[" + prefix + "] ";
        return this;
    }

    @Override
    public void accept(final @NotNull OutputFrame outputFrame) {
        var line = outputFrame.getUtf8String().replaceAll(LINE_BREAK_AT_END_REGEX, "");
        // filter verbose log messages and misleading errors
        if (line.contains("Failed to kill all the processes attached to cgroup") ||
                line.contains("Failed to set sysctl") ||
                line.contains("Failed to load kernel module") ||
                line.contains("desc = an error occurred when try to find container") ||
                line.contains("Unable to fetch coredns config map") ||
                line.contains("Skipping API") ||
                line.contains("Adding GroupVersion") ||
                line.contains("quota admission added evaluator for") ||
                line.contains("clearQuota called, but quotas disabled") ||
                line.contains("metrics.k8s.io") ||
                line.contains("certificate CN") ||
                line.contains("generated self-signed CA certificate") ||
                line.contains("cache.go") ||
                line.contains("garbagecollector.go") ||
                line.contains("logs.go") ||
                line.contains("operation_generator.go") ||
                line.contains("pod_startup_latency_tracker.go") ||
                line.contains("resource_quota_monitor.go") ||
                line.contains("shared_informer.go")) {
            return;
        }
        try {
            // W0531 10:37:43.289602      76 script_file.go:123] ...
            final var matcher = K8S_LOG_PREFIX.matcher(line);
            if (matcher.matches()) {
                // prefix the message with [ to complete the [script_file:123]
                line = "[" + matcher.group(1);
                return;
            }
            // time="2023-05-31T10:37:42Z" level=info msg="..."
            // the startup logs can be multi-lined if the message is too long (so we first remove the trailing quote,
            // then we can always match the first line, and the second line will have no trailing quote)
            if (line.endsWith("\"")) {
                line = line.substring(0, line.length() - 1);
            }
            final var startupMatcher = K8S_STARTUP_PREFIX.matcher(line);
            if (startupMatcher.matches()) {
                // prefix the message with the captured [LEVEL]
                line = "[" + startupMatcher.group(1).toUpperCase() + "] " + startupMatcher.group(2);
            }
        } finally {
            logger.info("{}{}", prefix, line);
        }
    }
}
