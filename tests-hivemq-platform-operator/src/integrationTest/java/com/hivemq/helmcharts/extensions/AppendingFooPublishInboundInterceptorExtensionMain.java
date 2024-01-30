package com.hivemq.helmcharts.extensions;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class AppendingFooPublishInboundInterceptorExtensionMain extends BaseAppendingPublishInboundInterceptorExtensionMain {

    @Override
    @NotNull String getAdditionalPayload() {
        return "-foo";
    }
}
