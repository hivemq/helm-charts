package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CompletableFutureUtil {

    private CompletableFutureUtil() {
    }

    /**
     * Invokes {@link CompletableFuture#get()} without throwing exceptions.
     *
     * @param future         the {@link CompletableFuture} to use
     * @param exceptionValue the return value if the invocation throws an exception
     * @return the result value,
     *         {@code exceptionValue} if the waiting is interrupted
     *         or the invocation throws an exception
     */
    public static <T> @Nullable T get(final @NotNull CompletableFuture<T> future, final @Nullable T exceptionValue) {
        return get(future, exceptionValue, exceptionValue);
    }

    /**
     * Invokes {@link CompletableFuture#get()} without throwing exceptions.
     *
     * @param future           the {@link CompletableFuture} to use
     * @param interruptedValue the return value if the thread is interrupted while waiting
     * @param exceptionValue   the return value if the invocation throws an {@link ExecutionException}
     * @return the result value,
     *         {@code interruptedValue} if the waiting is interrupted,
     *         {@code exceptionValue} if the invocation throws an exception
     */
    public static <T> @Nullable T get(
            final @NotNull CompletableFuture<T> future,
            final @Nullable T interruptedValue,
            final @Nullable T exceptionValue) {
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return interruptedValue;
        } catch (final ExecutionException e) {
            return exceptionValue;
        }
    }

    /**
     * Invokes {@link CompletableFuture#get(long, TimeUnit)} without throwing exceptions.
     *
     * @param future         the {@link CompletableFuture} to use
     * @param timeout        the maximum time to wait
     * @param timeUnit       the time unit of the {@code timeout} argument
     * @param exceptionValue the return value if the invocation throws an exception
     * @return the result value,
     *         {@code exceptionValue} if the waiting is interrupted, the invocation throws an exception
     *         or the invocation times out
     */
    public static <T> @Nullable T get(
            final @NotNull CompletableFuture<T> future,
            final long timeout,
            final @NotNull TimeUnit timeUnit,
            final @Nullable T exceptionValue) {
        return get(future, timeout, timeUnit, exceptionValue, exceptionValue, exceptionValue);
    }

    /**
     * Invokes {@link CompletableFuture#get(long, TimeUnit)} without throwing exceptions.
     *
     * @param future           the {@link CompletableFuture} to use
     * @param timeout          the maximum time to wait
     * @param timeUnit         the time unit of the {@code timeout} argument
     * @param interruptedValue the return value if the thread is interrupted while waiting
     * @param exceptionValue   the return value if the invocation throws an {@link ExecutionException}
     * @param timeoutValue     the return value if the invocation throws an {@link TimeoutException}
     * @return the result value,
     *         {@code interruptedValue} if the waiting is interrupted,
     *         {@code exceptionValue} if the invocation throws an exception
     *         or {@code timeoutValue} if the invocation times out
     */
    public static <T> @Nullable T get(
            final @NotNull CompletableFuture<T> future,
            final long timeout,
            final @NotNull TimeUnit timeUnit,
            final @Nullable T interruptedValue,
            final @Nullable T exceptionValue,
            final @Nullable T timeoutValue) {
        try {
            return future.get(timeout, timeUnit);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return interruptedValue;
        } catch (final ExecutionException e) {
            return exceptionValue;
        } catch (final TimeoutException e) {
            return timeoutValue;
        }
    }
}
