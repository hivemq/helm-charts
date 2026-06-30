package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.hivemq.helmcharts.testcontainer.K3sRuntimeStallDetector.THRESHOLD;
import static com.hivemq.helmcharts.testcontainer.K3sRuntimeStallDetector.WINDOW_NANOS;
import static org.assertj.core.api.Assertions.assertThat;

class K3sRuntimeStallDetectorTest {

    private static final @NotNull String STALL_LINE =
            "RunPodSandbox from runtime service failed: failed to reserve sandbox name \"x\" is reserved for \"y\"\n";

    @TempDir
    private @NotNull Path tmp;

    private final @NotNull AtomicInteger exitCalls = new AtomicInteger();
    private final @NotNull AtomicInteger lastExitCode = new AtomicInteger(-1);
    private final @NotNull AtomicLong clock = new AtomicLong();

    @Test
    @Timeout(10)
    void accept_belowThreshold_doesNotExit() {
        final var detector = newDetector();
        for (var i = 0; i < THRESHOLD - 1; i++) {
            detector.accept(STALL_LINE);
        }
        assertThat(exitCalls).hasValue(0);
        assertThat(lastExitCode).hasValue(-1);
    }

    @Test
    @Timeout(10)
    void accept_atThreshold_exitsOnceWithExitCode() {
        final var detector = newDetector();
        for (var i = 0; i < THRESHOLD; i++) {
            detector.accept(STALL_LINE);
        }
        assertThat(exitCalls).hasValue(1);
        assertThat(lastExitCode).hasValue(K3sRuntimeStallDetector.EXIT_CODE);
    }

    @Test
    @Timeout(10)
    void accept_pastThreshold_exitsOnlyOnce() {
        final var detector = newDetector();
        for (var i = 0; i < THRESHOLD * 3; i++) {
            detector.accept(STALL_LINE);
        }
        assertThat(exitCalls).hasValue(1);
        assertThat(lastExitCode).hasValue(K3sRuntimeStallDetector.EXIT_CODE);
    }

    @Test
    @Timeout(10)
    void accept_nonStallFrames_areIgnored() {
        final var detector = newDetector();
        for (var i = 0; i < 1_000; i++) {
            detector.accept("level=info msg=\"reconciling pod\" healthy\n");
        }
        assertThat(exitCalls).hasValue(0);
        assertThat(lastExitCode).hasValue(-1);
    }

    @Test
    @Timeout(10)
    void accept_markerSplitAcrossFrames_isCounted() {
        final var detector = newDetector();
        // each stall line is delivered as two frames split in the middle of the marker
        for (var i = 0; i < THRESHOLD; i++) {
            detector.accept("RunPodSandbox failed: failed to reserve ");
            detector.accept("sandbox name \"x\" is reserved for \"y\"\n");
        }
        assertThat(exitCalls).hasValue(1);
        assertThat(lastExitCode).hasValue(K3sRuntimeStallDetector.EXIT_CODE);
    }

    @Test
    @Timeout(10)
    void accept_multipleMarkersInOneFrame_areCounted() {
        final var detector = newDetector();
        detector.accept(STALL_LINE.repeat(THRESHOLD));
        assertThat(exitCalls).hasValue(1);
        assertThat(lastExitCode).hasValue(K3sRuntimeStallDetector.EXIT_CODE);
    }

    @Test
    @Timeout(10)
    void accept_hitsOlderThanWindow_arePruned() {
        final var detector = newDetector();
        // 29 hits, then jump past the window so they all expire
        for (var i = 0; i < THRESHOLD - 1; i++) {
            detector.accept(STALL_LINE);
        }
        clock.addAndGet(WINDOW_NANOS + 1);
        // another 29: without pruning the running total would be 58 and trip; with pruning only these 29 remain
        for (var i = 0; i < THRESHOLD - 1; i++) {
            detector.accept(STALL_LINE);
        }
        assertThat(exitCalls).hasValue(0);
        assertThat(lastExitCode).hasValue(-1);
    }

    @Test
    @Timeout(10)
    void accept_atThreshold_writesSyntheticJUnitReport() throws Exception {
        // nested path proves the detector creates the parent directories
        final var reportFile = tmp.resolve("nested").resolve("TEST-stall.xml");
        final var detector = newDetector(reportFile);
        for (var i = 0; i < THRESHOLD; i++) {
            detector.accept(STALL_LINE);
        }
        assertThat(reportFile).exists();
        final var xml = Files.readString(reportFile);
        assertThat(xml).contains("<testcase")
                .contains("name=\"k3sContainerdRuntimeStalled\"")
                .contains("<error message=")
                .contains("K3s containerd runtime stalled");
    }

    private @NotNull K3sRuntimeStallDetector newDetector() {
        return newDetector(tmp.resolve("report.xml"));
    }

    private @NotNull K3sRuntimeStallDetector newDetector(final @NotNull Path reportFile) {
        return new K3sRuntimeStallDetector(reportFile, code -> {
            exitCalls.incrementAndGet();
            lastExitCode.set(code);
        }, clock::get);
    }
}
