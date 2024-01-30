package com.hivemq.helmcharts.extensions;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AppendingBazPublishInboundInterceptorExtensionMain extends BaseAppendingPublishInboundInterceptorExtensionMain {

    @Override
    @NotNull String getAdditionalPayload() {
        return "-baz";
    }
}
