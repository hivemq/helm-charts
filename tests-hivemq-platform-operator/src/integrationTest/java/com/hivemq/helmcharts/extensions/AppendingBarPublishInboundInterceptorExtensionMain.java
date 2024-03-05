package com.hivemq.helmcharts.extensions;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AppendingBarPublishInboundInterceptorExtensionMain
        extends BaseAppendingPublishInboundInterceptorExtensionMain {

    @Override
    @NotNull String getAdditionalPayload() {
        return "-bar";
    }

    @Override
    @NotNull String getName() {
        return "HiveMQ Bar-Appending PublishInboundInterceptor Extension";
    }
}
