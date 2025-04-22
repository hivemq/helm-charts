package com.hivemq.helmcharts.extensions.custom;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AppendingBarPublishInboundInterceptorExtensionMain
        extends BaseAppendingPublishInboundInterceptorExtensionMain {

    @Override
    public @NotNull String getAdditionalPayload() {
        return "-bar";
    }

    @Override
    public @NotNull String getName() {
        return "HiveMQ Bar-Appending PublishInboundInterceptor Extension";
    }
}
