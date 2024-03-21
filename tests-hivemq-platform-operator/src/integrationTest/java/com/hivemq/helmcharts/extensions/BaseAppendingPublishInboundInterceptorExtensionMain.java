package com.hivemq.helmcharts.extensions;

import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import com.hivemq.extension.sdk.api.services.Services;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class BaseAppendingPublishInboundInterceptorExtensionMain implements ExtensionMain {

    private static final @NotNull Logger LOG =
            LoggerFactory.getLogger(BaseAppendingPublishInboundInterceptorExtensionMain.class);

    @Override
    public void extensionStart(
            final @NotNull ExtensionStartInput extensionStartInput,
            final @NotNull ExtensionStartOutput extensionStartOutput) {
        LOG.info("{} started", getName());

        Services.initializerRegistry()
                .setClientInitializer((initializerInput, clientContext) -> clientContext.addPublishInboundInterceptor(
                        new AppendingPublishInboundInterceptor(getClass(), getAdditionalPayload())));
    }

    @Override
    public void extensionStop(
            final @NotNull ExtensionStopInput extensionStopInput,
            final @NotNull ExtensionStopOutput extensionStopOutput) {
        LOG.info("{} stopped", getName());
    }

    abstract @NotNull String getAdditionalPayload();

    abstract @NotNull String getName();
}
