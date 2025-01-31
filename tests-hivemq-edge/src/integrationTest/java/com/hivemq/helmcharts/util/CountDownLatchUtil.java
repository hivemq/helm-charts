package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CountDownLatchUtil {

    private CountDownLatchUtil() {
    }

    /**
     * Invokes {@link CountDownLatch#await(long, TimeUnit)} without throwing exceptions.
     *
     * @param latch            the {@link CountDownLatch} to use
     * @param timeout          the maximum time to wait
     * @param timeUnit         the time unit of the {@code timeout} argument
     * @param interruptedValue the return value if the thread is interrupted while waiting
     * @return {@code true} if the count reached zero, {@code false} if
     *         the waiting time elapsed before the count reached zero and
     *         {@code interruptedValue} if the waiting is interrupted
     */
    public static boolean await(
            final @NotNull CountDownLatch latch,
            final long timeout,
            final @NotNull TimeUnit timeUnit,
            final boolean interruptedValue) {
        try {
            return latch.await(timeout, timeUnit);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return interruptedValue;
        }
    }
}
