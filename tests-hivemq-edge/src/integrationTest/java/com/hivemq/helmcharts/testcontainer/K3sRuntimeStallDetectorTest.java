package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.hivemq.helmcharts.testcontainer.K3sRuntimeStallDetector.THRESHOLD;
import static com.hivemq.helmcharts.testcontainer.K3sRuntimeStallDetector.WINDOW_NANOS;
import static org.assertj.core.api.Assertions.assertThat;

class K3sRuntimeStallDetectorTest {

    private static final @NotNull String STALL_LINE =
            "RunPodSandbox from runtime service failed: failed to reserve sandbox name \"x\" is reserved for \"y\"\n";

    private final @NotNull AtomicBoolean stallFlag = new AtomicBoolean();
    private final @NotNull K3sRuntimeStallDetector detector =
            new K3sRuntimeStallDetector(System::nanoTime, stallFlag, count -> {});

    @Test
    @Timeout(10)
    void accept_belowThreshold_recordsNothing() {
        for (var i = 0; i < THRESHOLD - 1; i++) {
            detector.accept(STALL_LINE);
        }
        assertThat(stallFlag).isFalse();
    }

    @Test
    @Timeout(10)
    void accept_atThreshold_recordsStall() {
        for (var i = 0; i < THRESHOLD; i++) {
            detector.accept(STALL_LINE);
        }
        assertThat(stallFlag).isTrue();
    }

    @Test
    @Timeout(10)
    void accept_nonStallFrames_areIgnored() {
        for (var i = 0; i < 1_000; i++) {
            detector.accept("level=info msg=\"reconciling pod\" healthy\n");
        }
        assertThat(stallFlag).isFalse();
    }

    @Test
    @Timeout(10)
    void accept_markerSplitAcrossFrames_isCounted() {
        // each stall line is delivered as two frames split in the middle of the marker
        for (var i = 0; i < THRESHOLD; i++) {
            detector.accept("RunPodSandbox failed: failed to reserve ");
            detector.accept("sandbox name \"x\" is reserved for \"y\"\n");
        }
        assertThat(stallFlag).isTrue();
    }

    @Test
    @Timeout(10)
    void accept_multipleMarkersInOneFrame_areCounted() {
        detector.accept(STALL_LINE.repeat(THRESHOLD));
        assertThat(stallFlag).isTrue();
    }

    @Test
    @Timeout(10)
    void accept_hitsOlderThanWindow_arePruned() {
        final var clock = new AtomicLong();
        final var clockDetector = new K3sRuntimeStallDetector(clock::get, stallFlag, count -> {});
        // 29 hits, then jump past the window so they all expire
        for (var i = 0; i < THRESHOLD - 1; i++) {
            clockDetector.accept(STALL_LINE);
        }
        clock.addAndGet(WINDOW_NANOS + 1);
        // another 29: without pruning the running total would be 58 and trip; with pruning only these 29 remain
        for (var i = 0; i < THRESHOLD - 1; i++) {
            clockDetector.accept(STALL_LINE);
        }
        assertThat(stallFlag).isFalse();
    }

    @Test
    @Timeout(10)
    void accept_atThreshold_printsThreadDump() {
        final var dumpDetector =
                new K3sRuntimeStallDetector(System::nanoTime, stallFlag, K3sRuntimeStallDetector::logStallAndDumpThreads);
        final var originalErr = System.err;
        final var captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            for (var i = 0; i < THRESHOLD; i++) {
                dumpDetector.accept(STALL_LINE);
            }
        } finally {
            System.setErr(originalErr);
        }
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains("Thread dump captured at the K3s runtime stall")
                .contains("state=")
                .contains(Thread.currentThread().getName());
    }
}
