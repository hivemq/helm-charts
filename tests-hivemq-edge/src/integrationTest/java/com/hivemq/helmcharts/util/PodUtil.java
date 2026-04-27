package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PodUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(PodUtil.class);

    private PodUtil() {
    }

    /**
     * Executes a command on a pod.
     */
    public static @NotNull ExecResult execute(
            final @NotNull KubernetesClient client,
            final @NotNull String namespace,
            final @NotNull String podName,
            final @NotNull String containerName,
            final @NotNull String... command) {
        // calling close() on ByteArrayOutputStream has no effect, so we don't need a try-with-resource block
        final var output = new ByteArrayOutputStream();
        final var error = new ByteArrayOutputStream();
        final var commandString = Arrays.toString(command);
        final var listener = new ExecWaitListener(commandString, podName, output, error);
        final var podResource = client.pods().inNamespace(namespace).withName(podName);
        if (isPodGone(podResource, podName)) {
            throw new IllegalStateException("Pod '" +
                    podName +
                    "' is not running, cannot execute command '" +
                    commandString +
                    "'");
        }
        final var execWatch = podResource.inContainer(containerName)
                .writingOutput(output)
                .writingError(error)
                .usingListener(listener)
                .exec(command);
        return new ExecResult(execWatch, listener);
    }

    /**
     * Queries the K8s API for the given {@link PodResource} and checks if it's still present, that its phase is still
     * "Running" and whether it's running the {@code HIVEMQ_CONTAINER_NAME} container.
     *
     * @return {@code true} if the pod has terminated, {@code false} if it's still running and present and can therefore
     *         be operated upon
     */
    private static boolean isPodGone(final @NotNull PodResource podResource, final @NotNull String podName) {
        final var pod = podResource.get();
        if (pod == null || pod.getStatus() == null || !"Running".equalsIgnoreCase(pod.getStatus().getPhase())) {
            LOG.debug("Pod '{}' is not running", podName);
            return true;
        }
        return false;
    }

    public static class ExecResult {

        private final long started = System.currentTimeMillis();
        private final @NotNull ExecWatch execWatch;
        private final @NotNull ExecWaitListener listener;

        ExecResult(final @NotNull ExecWatch execWatch, final @NotNull ExecWaitListener listener) {
            this.execWatch = execWatch;
            this.listener = listener;
        }

        @SuppressWarnings("unused")
        public long getStarted() {
            return started;
        }

        public boolean await(final long timeout, final @NotNull TimeUnit timeUnit) {
            return CountDownLatchUtil.await(listener.resultIsSetLatch, timeout, timeUnit, false) &&
                    CompletableFutureUtil.get(execWatch.exitCode(), timeout, timeUnit, null) != null;
        }

        @SuppressWarnings("unused")
        public boolean isDone() {
            return listener.resultIsSetLatch.getCount() == 0 && execWatch.exitCode().isDone();
        }

        @SuppressWarnings("unused")
        public @Nullable Integer exitCode() {
            final var exitCodeFuture = execWatch.exitCode();
            if (!exitCodeFuture.isDone()) {
                return null;
            }
            return CompletableFutureUtil.get(exitCodeFuture, null);
        }

        @SuppressWarnings("unused")
        public @NotNull String getPodName() {
            return listener.podName;
        }

        public @Nullable String getOutput() {
            listener.setResult(null);
            return listener.outputString;
        }

        public @Nullable String getError() {
            listener.setResult(null);
            return listener.errorString;
        }

        public void close() {
            execWatch.close();
            listener.setResult(null);
        }
    }

    static class ExecWaitListener implements ExecListener {

        private final @NotNull AtomicBoolean resultIsSet = new AtomicBoolean();
        private final @NotNull CountDownLatch resultIsSetLatch = new CountDownLatch(1);

        private final @NotNull String command;
        private final @NotNull String podName;
        private final @NotNull ByteArrayOutputStream output;
        private final @NotNull ByteArrayOutputStream error;

        private @Nullable String outputString;
        private @Nullable String errorString;

        ExecWaitListener(
                final @NotNull String command,
                final @NotNull String podName,
                final @NotNull ByteArrayOutputStream output,
                final @NotNull ByteArrayOutputStream error) {
            this.command = command;
            this.podName = podName;
            this.output = output;
            this.error = error;
            this.errorString = null;
            this.outputString = null;
        }

        @Override
        public void onOpen() {
            LOG.trace("Opened Websocket for command '{}' on pod '{}'", command, podName);
        }

        @Override
        public void onFailure(final @NotNull Throwable t, final @NotNull Response response) {
            LOG.warn("Failed to execute command '{}' on pod '{}': {}", command, podName, response, t);
            try {
                setResult(response.body());
            } catch (final IOException e) {
                setResult(t.toString());
            }
        }

        @Override
        public void onClose(final int code, final @NotNull String reason) {
            LOG.trace("Closing Websocket '{}' on pod '{}' (WebSocket code: {}) (exit reason: '{}')",
                    command,
                    podName,
                    code,
                    reason);
            setResult(null);
        }

        @Override
        public void onExit(final int code, final @NotNull Status status) {
            LOG.debug("Received status for command '{}' on pod '{}' (exit code: {})", command, podName, code);
            setResult(null);
        }

        private void setResult(final @Nullable String failureString) {
            if (!resultIsSet.compareAndSet(false, true)) {
                return;
            }
            try {
                output.flush();
            } catch (final IOException e) {
                LOG.warn("Failed to execute command '{}' on pod '{}': Could not flush output stream",
                        command,
                        podName,
                        e);
            }
            if (output.size() > 0) {
                outputString = output.toString().trim();
            }
            try {
                error.flush();
            } catch (final IOException e) {
                LOG.warn("Failed to execute command '{}' on pod '{}': Could not flush error stream",
                        command,
                        podName,
                        e);
            }
            if (failureString != null) {
                errorString = failureString;
            } else if (error.size() > 0) {
                errorString = error.toString().trim();
            }
            resultIsSetLatch.countDown();
        }
    }
}
