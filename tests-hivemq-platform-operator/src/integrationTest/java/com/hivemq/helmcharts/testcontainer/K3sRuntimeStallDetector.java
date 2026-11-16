package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/**
 * Watches the K3s container log stream for a stalled containerd runtime and records the stall when detected.
 * <p>
 * When containerd inside the K3s container stalls (observed as {@code StopContainer ... DeadlineExceeded}), it leaks
 * the sandbox name reservation and never recovers: every following {@code RunPodSandbox} fails with
 * {@code failed to reserve sandbox name ... is reserved for <id>}. Pods never reach Ready, so the running test fails
 * on its own {@code @Timeout} or an Awaitility timeout, but with an exception that hides the cause.
 * <p>
 * This detector runs on the docker log-stream thread, so it cannot fail the test itself. Instead, it flips an in-JVM
 * flag and prints a thread dump for diagnosis. {@link K3sStallFailureExtension} reads {@link #hasDetectedStall()} when
 * a test fails and relabels the failure with the stall as the cause, and {@link HelmChartContainerExtension} reads the
 * same flag to fail the following tests fast instead of running them on the stalled runtime.
 */
class K3sRuntimeStallDetector implements Consumer<String> {

    static final int THRESHOLD = 30;
    static final @NotNull Duration WINDOW = Duration.ofMinutes(2);
    static final long WINDOW_NANOS = WINDOW.toNanos();

    private static final @NotNull String STALL_MARKER = "failed to reserve sandbox name";

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(K3sRuntimeStallDetector.class);

    // the stall of this JVM, which the container extension records for the rest of the CI job; also gates the one-time
    // dump
    private static final @NotNull AtomicBoolean stallDetected = new AtomicBoolean();

    private final @NotNull Deque<Long> hits = new ArrayDeque<>();

    // testcontainers delivers frames from a single docker log-stream thread, so the residual tail needs no guarding
    private @NotNull String residual = "";

    private final @NotNull LongSupplier nanoClock;
    private final @NotNull AtomicBoolean stallFlag;
    private final @NotNull IntConsumer onStall;

    K3sRuntimeStallDetector() {
        this(System::nanoTime, stallDetected, K3sRuntimeStallDetector::logStallAndDumpThreads);
    }

    // tests record into their own flag, so a stall recorded by an integration test in the same JVM stays set, and pass
    // their own stall action, so a test stall prints no banner and no thread dump into the build log
    @VisibleForTesting
    K3sRuntimeStallDetector(
            final @NotNull LongSupplier nanoClock,
            final @NotNull AtomicBoolean stallFlag,
            final @NotNull IntConsumer onStall) {
        this.nanoClock = nanoClock;
        this.stallFlag = stallFlag;
        this.onStall = onStall;
    }

    static boolean hasDetectedStall() {
        return stallDetected.get();
    }

    static void recordStall() {
        stallDetected.set(true);
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
        final var count = recordHitsAndCountWithinWindow(occurrences);
        if (count < THRESHOLD || !stallFlag.compareAndSet(false, true)) {
            return;
        }
        onStall.accept(count);
    }

    private int recordHitsAndCountWithinWindow(final int occurrences) {
        // nanoTime() is monotonic, so the sliding window is immune to wall-clock jumps (NTP, host time changes)
        final var now = nanoClock.getAsLong();
        for (var i = 0; i < occurrences; i++) {
            hits.addLast(now);
        }
        while (!hits.isEmpty() && hits.peekFirst() < now - WINDOW_NANOS) {
            hits.pollFirst();
        }
        return hits.size();
    }

    @VisibleForTesting
    static void logStallAndDumpThreads(final int count) {
        LOG.error("""
                        ============================================================
                        K3s containerd runtime is stalled and cannot recover:
                        '{}' repeated {} times within {} seconds.
                        Recording the stall; the running test fails via its timeout and the failure is relabeled.
                        ============================================================""",
                STALL_MARKER,
                count,
                WINDOW.toSeconds());
        // the detector runs on the log-stream thread and cannot fail the test itself; dump the threads so the frame the
        // test is parked in (Awaitility, informer, a native call, ...) is in the build log for diagnosis
        printThreadDump();
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

    private static void printThreadDump() {
        final var dump = new StringBuilder("Thread dump captured at the K3s runtime stall:\n\n");
        Thread.getAllStackTraces().forEach((thread, frames) -> {
            dump.append('"').append(thread.getName()).append('"');
            if (thread.isDaemon()) {
                dump.append(" daemon");
            }
            dump.append(" state=").append(thread.getState()).append('\n');
            for (final var frame : frames) {
                dump.append("\tat ").append(frame).append('\n');
            }
            dump.append('\n');
        });
        System.err.println(dump);
        System.err.flush();
    }
}
