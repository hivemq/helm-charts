package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/**
 * Watches the K3s container log stream for a stalled containerd runtime and hard-kills the JVM when detected.
 * <p>
 * When containerd inside the K3s container stalls (observed as {@code StopContainer ... DeadlineExceeded}), it leaks
 * the sandbox name reservation and never recovers: every following {@code RunPodSandbox} fails with
 * {@code failed to reserve sandbox name ... is reserved for <id>}. Pods never reach Ready, the test blocks in a
 * fabric8 watch / Awaitility retry loop that does not honour {@link Thread#interrupt()}, and JUnit's interrupt-based
 * {@code @Timeout} can therefore never fire. The test hangs until the CI step is force-killed at the 30-minute limit.
 * <p>
 * The runtime cannot self-heal from this state and the trigger (host CPU/IO starvation on the shared runner) is out of
 * our control. The only remaining lever is to fail fast: once the unrecoverable signature repeats past a threshold
 * within a sliding window, terminate the JVM with {@link Runtime#halt(int)} so the build fails in seconds instead of
 * burning the full step timeout. {@code halt} is used over {@code System.exit} on purpose: shutdown hooks would block
 * on the same stalled runtime.
 */
class K3sRuntimeStallDetector implements Consumer<String> {

    static final int EXIT_CODE = 66;
    static final int THRESHOLD = 30;
    static final @NotNull Duration WINDOW = Duration.ofMinutes(2);
    static final long WINDOW_NANOS = WINDOW.toNanos();

    private static final @NotNull String STALL_MARKER = "failed to reserve sandbox name";

    // resolved against the Gradle test worker working directory (the module project dir), matching where Gradle itself
    // writes its reports, so the CI dorny/test-reporter step picks it up
    private static final @NotNull String REPORT_CLASS_NAME = K3sRuntimeStallDetector.class.getName();
    private static final @NotNull Path REPORT_FILE =
            Path.of("build", "test-results", "integrationTest", "TEST-" + REPORT_CLASS_NAME + ".xml");

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(K3sRuntimeStallDetector.class);

    private final @NotNull Deque<Long> hits = new ArrayDeque<>();
    private final @NotNull AtomicBoolean halted = new AtomicBoolean();

    // testcontainers delivers frames from a single docker log-stream thread, so the residual tail needs no guarding
    private @NotNull String residual = "";

    private final @NotNull IntConsumer exitHandler;
    private final @NotNull Path reportFile;
    private final @NotNull LongSupplier nanoClock;

    K3sRuntimeStallDetector() {
        this(code -> Runtime.getRuntime().halt(code), REPORT_FILE, System::nanoTime);
    }

    // package-private so the unit test can inject the exit action, report path, and clock without killing the JVM
    K3sRuntimeStallDetector(
            final @NotNull IntConsumer exitHandler,
            final @NotNull Path reportFile,
            final @NotNull LongSupplier nanoClock) {
        this.exitHandler = exitHandler;
        this.reportFile = reportFile;
        this.nanoClock = nanoClock;
    }

    @Override
    public void accept(final @NotNull String frame) {
        // testcontainers may split a log line across frames or batch several into one; join with the retained tail and
        // count every occurrence so a boundary split is not missed and a batched frame is not undercounted
        final var text = residual + frame;
        final var tailLength = STALL_MARKER.length() - 1;
        residual = text.length() > tailLength ? text.substring(text.length() - tailLength) : text;
        final var occurrences = countOccurrences(text);
        if (occurrences == 0) {
            return;
        }
        // nanoTime() is monotonic, so the sliding window is immune to wall-clock jumps (NTP, host time changes)
        final var now = nanoClock.getAsLong();
        final var count = new AtomicInteger();
        synchronized (hits) {
            for (var i = 0; i < occurrences; i++) {
                hits.addLast(now);
            }
            while (!hits.isEmpty() && hits.peekFirst() < now - WINDOW_NANOS) {
                hits.pollFirst();
            }
            count.set(hits.size());
        }
        if (count.get() < THRESHOLD || !halted.compareAndSet(false, true)) {
            return;
        }
        LOG.error("""
                        ============================================================
                        K3s containerd runtime is stalled and cannot recover:
                        '{}' repeated {} times within {} seconds.
                        Hard-killing the JVM (exit code {}) to fail fast instead of \
                        hanging until the CI step timeout.
                        ============================================================""",
                STALL_MARKER,
                count.get(),
                WINDOW.toSeconds(),
                EXIT_CODE);
        // halt() skips JUnit's lifecycle, so the stalled test never gets a report; write a synthetic one first, so the
        // failure surfaces as a clear annotation instead of a silent worker crash
        writeStallReport(count.get());
        // halt() runs no shutdown hooks and logback may not flush its appenders, so print to stderr directly to keep the
        // banner in the build log
        System.err.printf(
                "K3s containerd runtime stalled: '%s' repeated %d times within %d seconds; hard-killing JVM (exit code %d).%n",
                STALL_MARKER,
                count.get(),
                WINDOW.toSeconds(),
                EXIT_CODE);
        System.err.flush();
        // default exitHandler is halt() not exit(): exit() runs the testcontainers shutdown hook, which docker-stops the
        // stalled K3s container and blocks on the same stalled containerd, re-hanging the very thing we are escaping.
        // Ryuk reaps the abandoned container, so skipping the hook leaks nothing.
        exitHandler.accept(EXIT_CODE);
    }

    private static int countOccurrences(final @NotNull String text) {
        var count = 0;
        var index = text.indexOf(STALL_MARKER);
        while (index >= 0) {
            count++;
            index = text.indexOf(STALL_MARKER, index + STALL_MARKER.length());
        }
        return count;
    }

    private void writeStallReport(final int count) {
        final var message =
                "K3s containerd runtime stalled: '%s' repeated %d times within %d seconds. The runtime cannot recover; the JVM was hard-killed (exit code %d) to fail fast instead of hanging until the CI step timeout.".formatted(
                        STALL_MARKER,
                        count,
                        WINDOW.toSeconds(),
                        EXIT_CODE);
        final var escaped = escapeXml(message);
        final var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="%s" tests="1" failures="0" errors="1" skipped="0" time="0">
                  <testcase classname="%s" name="k3sContainerdRuntimeStalled" time="0">
                    <error message="%s">%s</error>
                  </testcase>
                </testsuite>
                """.formatted(REPORT_CLASS_NAME, REPORT_CLASS_NAME, escaped, escaped);
        try {
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile, xml);
            LOG.error("Wrote synthetic JUnit report for the K3s runtime stall to {}", reportFile.toAbsolutePath());
        } catch (final IOException e) {
            LOG.error("Could not write synthetic JUnit report for the K3s runtime stall", e);
        }
    }

    private static @NotNull String escapeXml(final @NotNull String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
