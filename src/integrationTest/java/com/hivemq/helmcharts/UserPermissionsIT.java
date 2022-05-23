package com.hivemq.helmcharts;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class UserPermissionsIT {

    private static final @NotNull String resourcesPath = "/test";
    private static final @NotNull String customValuesPath = resourcesPath+"/src/integrationTest/resources/permissionsDeployment.yaml";


    @Test
    public void withCustomImage_mqttMessagePublishedReceived() throws Exception {
        // We need at least 6GB of RAM for this test

    }
}
