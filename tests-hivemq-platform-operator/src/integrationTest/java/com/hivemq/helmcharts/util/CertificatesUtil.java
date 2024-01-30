package com.hivemq.helmcharts.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class CertificatesUtil {

    private static final @NotNull Logger LOG = LoggerFactory.getLogger(CertificatesUtil.class);

    private CertificatesUtil() {
    }

    public static void generateCertificates(final @NotNull File path) throws IOException, InterruptedException {
        final var processBuilder = new ProcessBuilder("bash",
                "-c",
                "source ./build/resources/integrationTest/scripts/generate-certificates.sh && tls_certs " +
                        path.getAbsolutePath());
        processBuilder.redirectErrorStream(true);
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
