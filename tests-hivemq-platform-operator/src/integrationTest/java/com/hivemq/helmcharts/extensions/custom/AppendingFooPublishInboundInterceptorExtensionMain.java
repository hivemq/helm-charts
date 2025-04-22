package com.hivemq.helmcharts.extensions.custom;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AppendingFooPublishInboundInterceptorExtensionMain
        extends BaseAppendingPublishInboundInterceptorExtensionMain {

    @Override
    public @NotNull String getAdditionalPayload() {
        return "-foo";
    }

    @Override
    public @NotNull String getName() {
        return "HiveMQ Foo-Appending PublishInboundInterceptor Extension";
    }
}
