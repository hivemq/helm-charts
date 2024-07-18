package com.hivemq.helmcharts.util;

import io.fabric8.kubernetes.client.LocalPortForward;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class PrometheusUtil {

    private PrometheusUtil() {
    }

    public static @NotNull Map<String, Float> getPrometheusMetrics(
            final @NotNull LocalPortForward portForward,
            final @NotNull String path) throws Exception {
        try (final var client = HttpClient.newBuilder() //
                .followRedirects(HttpClient.Redirect.ALWAYS) //
                .connectTimeout(Duration.ofSeconds(10)) //
                .build()) {

            final var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + portForward.getLocalPort() + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new TreeMap<>(response.body()
                    .lines()
                    .filter(s -> !s.isBlank())
                    .filter(s -> !s.startsWith("#"))
                    .map(s -> s.split(" "))
                    .map(splits -> Map.entry(splits[0], Float.parseFloat(splits[1])))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
    }
}
