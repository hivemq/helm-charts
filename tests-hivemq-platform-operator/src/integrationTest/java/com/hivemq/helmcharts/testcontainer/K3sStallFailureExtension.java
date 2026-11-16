package com.hivemq.helmcharts.testcontainer;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

/**
 * Relabels a test failure that is a consequence of a detected K3s containerd runtime stall.
 * <p>
 * {@link K3sRuntimeStallDetector} runs on the docker log-stream thread and cannot fail the test itself, so a stall
 * surfaces only as the running test hitting its {@code @Timeout} or an Awaitility timeout, with an exception that hides
 * the cause. Once a stall has been recorded, this handler rethrows the failure wrapped so the reported failure names
 * the stall, keeping the original as the cause.
 */
public class K3sStallFailureExtension
        implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(
            final @NotNull ExtensionContext context,
            final @NotNull Throwable throwable) throws Throwable {
        throw relabel(throwable);
    }

    @Override
    public void handleBeforeAllMethodExecutionException(
            final @NotNull ExtensionContext context,
            final @NotNull Throwable throwable) throws Throwable {
        throw relabel(throwable);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(
            final @NotNull ExtensionContext context,
            final @NotNull Throwable throwable) throws Throwable {
        throw relabel(throwable);
    }

    @Override
    public void handleAfterEachMethodExecutionException(
            final @NotNull ExtensionContext context,
            final @NotNull Throwable throwable) throws Throwable {
        throw relabel(throwable);
    }

    @Override
    public void handleAfterAllMethodExecutionException(
            final @NotNull ExtensionContext context,
            final @NotNull Throwable throwable) throws Throwable {
        throw relabel(throwable);
    }

    private static @NotNull Throwable relabel(final @NotNull Throwable throwable) {
        // an aborted test (a failed assumption) is not a failure, so it stays aborted
        if (throwable instanceof TestAbortedException || !K3sRuntimeStallDetector.hasDetectedStall()) {
            return throwable;
        }
        return new AssertionError("K3s containerd runtime stalled; the attached failure is a consequence, not the cause.",
                throwable);
    }
}
