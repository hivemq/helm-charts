package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

@SuppressWarnings("unused")
public class CertificatesUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(CertificatesUtil.class);

    public static final @NotNull String DEFAULT_CHART_NAME = "platform";
    public static final @NotNull String DEFAULT_SERVICE = "secure-service";
    public static final @NotNull String DEFAULT_SERVICE_NAME =
            String.format("%s-%s", DEFAULT_SERVICE, DEFAULT_CHART_NAME);
    public static final @NotNull String DEFAULT_NAMESPACE = "default";
    public static final @NotNull String DEFAULT_KEYSTORE_PASSWORD = "key-changeme";
    public static final @NotNull String DEFAULT_PRIVATE_KEY_PASSWORD = "key-changeme";
    public static final @NotNull String DEFAULT_TRUSTSTORE_PASSWORD = "trust-changeme";

    public static final @NotNull String ENV_VAR_CHART_NAME = "CHART_NAME";
    public static final @NotNull String ENV_VAR_SERVICE = "SERVICE";
    public static final @NotNull String ENV_VAR_SERVICE_NAME = "SERVICE_NAME";
    public static final @NotNull String ENV_VAR_NAMESPACE = "NAMESPACE";
    public static final @NotNull String ENV_VAR_KEYSTORE_PASSWORD = "KEYSTORE_PASSWORD";
    public static final @NotNull String ENV_VAR_PRIVATE_KEY_PASSWORD = "PRIVATE_KEY_PASSWORD";
    public static final @NotNull String ENV_VAR_TRUSTSTORE_PASSWORD = "TRUSTSTORE_PASSWORD";


    private CertificatesUtil() {
    }

    public static void generateCertificates(final @NotNull File path) throws IOException, InterruptedException {
        generateCertificates(path, Map.of());
    }

    public static void generateCertificates(
            final @NotNull File path, final @NotNull Map<String, String> environmentVariables)
            throws IOException, InterruptedException {
        final var processBuilder = new ProcessBuilder("bash",
                "-c",
                "source ./build/resources/integrationTest/scripts/generate-certificates.sh " + path.getAbsolutePath());
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().putAll(environmentVariables);
        final var process = processBuilder.start();
        final var inputStream = process.getInputStream();
        final var reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            LOG.debug(line);
        }
        final var res = process.waitFor();
        if (res != 0) {
            throw new IOException("Error executing the script with exit result " + res);
        }
    }
}
