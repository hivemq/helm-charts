package com.hivemq.release;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * Consumes an {@link InputStream}.
 */
class StreamGobbler implements Runnable {

    private final @NotNull InputStream inputStream;
    private final @NotNull Consumer<String> consumer;

    StreamGobbler(final @NotNull InputStream inputStream, final @NotNull Consumer<String> consumer) {
        this.inputStream = inputStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
    }
}
