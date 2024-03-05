package com.hivemq.helmcharts.extensions;

import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.interceptor.publish.PublishInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class AppendingPublishInboundInterceptor implements PublishInboundInterceptor {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(AppendingPublishInboundInterceptor.class);

    private final @NotNull String logPrefix;
    private final @NotNull String additionalPayload;

    AppendingPublishInboundInterceptor(
            final @NotNull Class<? extends ExtensionMain> extensionClass,
            final @NotNull String additionalPayload) {
        this.logPrefix = extensionClass.getSimpleName();
        this.additionalPayload = additionalPayload;
    }

    @Override
    public void onInboundPublish(
            final @NotNull PublishInboundInput publishInboundInput,
            final @NotNull PublishInboundOutput publishInboundOutput) {
        final var publish = publishInboundInput.getPublishPacket();
        final var payload = StandardCharsets.UTF_8.decode(publish.getPayload().orElse(ByteBuffer.wrap(new byte[0])));
        final var newPayload = payload + additionalPayload;
        LOG.info("[{}] Modifying payload: {} -> {}", logPrefix, payload, newPayload);
        publishInboundOutput.getPublishPacket().setPayload(StandardCharsets.UTF_8.encode(newPayload));
    }
}
