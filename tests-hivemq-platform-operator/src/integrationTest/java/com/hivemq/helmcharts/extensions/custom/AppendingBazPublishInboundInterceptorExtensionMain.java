package com.hivemq.helmcharts.extensions.custom;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AppendingBazPublishInboundInterceptorExtensionMain
        extends BaseAppendingPublishInboundInterceptorExtensionMain {

    @Override
    public @NotNull String getAdditionalPayload() {
        return "-baz";
    }

    @Override
    public @NotNull String getName() {
        return "HiveMQ Baz-Appending PublishInboundInterceptor Extension";
    }
}
