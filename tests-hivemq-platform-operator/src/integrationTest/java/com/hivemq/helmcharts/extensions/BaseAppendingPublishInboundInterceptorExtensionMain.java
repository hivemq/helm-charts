package com.hivemq.helmcharts.extensions;

import com.google.common.base.Charsets;
import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.interceptor.publish.PublishInboundInterceptor;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundInput;
import com.hivemq.extension.sdk.api.interceptor.publish.parameter.PublishInboundOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import com.hivemq.extension.sdk.api.services.Services;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

@SuppressWarnings("unused")
abstract class BaseAppendingPublishInboundInterceptorExtensionMain implements ExtensionMain {

    private static final @NotNull Logger LOG =
            LoggerFactory.getLogger(BaseAppendingPublishInboundInterceptorExtensionMain.class);

    @Override
    public void extensionStart(
            final @NotNull ExtensionStartInput extensionStartInput,
            final @NotNull ExtensionStartOutput extensionStartOutput) {
        LOG.info("HiveMQ Appending PublishInboundInterceptor Extension started");

        Services.initializerRegistry()
                .setClientInitializer((initializerInput, clientContext) -> clientContext.addPublishInboundInterceptor(
                        new AppendingPublishInboundInterceptor(getClass(), getAdditionalPayload())));
    }

    @Override
    public void extensionStop(
            @NotNull final ExtensionStopInput extensionStopInput,
            @NotNull final ExtensionStopOutput extensionStopOutput) {
        LOG.info("HiveMQ Appending PublishInboundInterceptor Extension stopped");
    }

    abstract @NotNull String getAdditionalPayload();

    private static class AppendingPublishInboundInterceptor implements PublishInboundInterceptor {

        private final @NotNull String logPrefix;
        private final @NotNull String additionalPayload;

        private AppendingPublishInboundInterceptor(
                final @NotNull Class<? extends ExtensionMain> extensionClass,
                final @NotNull String additionalPayload) {
            this.logPrefix = extensionClass.getSimpleName();
            this.additionalPayload = additionalPayload;
        }

        @Override
        public void onInboundPublish(
                @NotNull final PublishInboundInput publishInboundInput,
                @NotNull final PublishInboundOutput publishInboundOutput) {
            final var publish = publishInboundInput.getPublishPacket();
            final var payload = Charsets.UTF_8.decode(publish.getPayload().orElse(ByteBuffer.wrap(new byte[0])));
            final var newPayload = payload + additionalPayload;
            LOG.info("[{}] Modifying payload: {} -> {}", logPrefix, payload, newPayload);
            publishInboundOutput.getPublishPacket().setPayload(Charsets.UTF_8.encode(newPayload));
        }
    }
}
