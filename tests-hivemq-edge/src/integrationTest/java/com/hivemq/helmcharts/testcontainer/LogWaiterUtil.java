package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class LogWaiterUtil implements BiConsumer<String, String> {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(LogWaiterUtil.class);

    private final @NotNull Map<String, Map<String, CompletableFuture<String>>> patterns = new ConcurrentHashMap<>();

    @Override
    public void accept(final @NotNull String prefix, final @NotNull String line) {
        final var prefixPatterns = getOrDefault(prefix);
        prefixPatterns.forEach((pattern, future) -> {
            if (line.matches(pattern)) {
                LOG.info("TEST Found log pattern on '{}': {}", prefix, pattern);
                prefixPatterns.remove(pattern);
                future.complete("[" + prefix + "] " + line);
            }
        });
    }

    public @NotNull CompletableFuture<String> waitFor(final @NotNull String prefix, final @NotNull String pattern) {
        LOG.info("TEST Waiting for log pattern on '{}': {}", prefix, pattern);
        return patterns.computeIfAbsent(prefix, s -> new ConcurrentHashMap<>())
                .computeIfAbsent(pattern, s -> new CompletableFuture<>());
    }

    private @NotNull Map<String, CompletableFuture<String>> getOrDefault(final @NotNull String prefix) {
        for (final var patternEntry : patterns.entrySet()) {
            if (prefix.matches(patternEntry.getKey())) {
                return patternEntry.getValue();
            }
        }
        return patterns.getOrDefault(prefix, Map.of());
    }
}
