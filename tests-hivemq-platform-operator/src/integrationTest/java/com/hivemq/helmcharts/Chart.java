package com.hivemq.helmcharts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.marcnuri.helm.SearchResult;
import org.jetbrains.annotations.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Chart {

    @JsonProperty("name")
    private @NotNull String name;

    @JsonProperty("description")
    private @NotNull String description;

    @JsonProperty("appVersion")
    private @NotNull String appVersion;

    @JsonProperty("version")
    @JsonDeserialize(using = Version.Deserializer.class)
    private @NotNull Version chartVersion;

    public @NotNull String getName() {
        return name;
    }

    public @NotNull String getDescription() {
        return description;
    }

    public @NotNull String getAppVersion() {
        return appVersion;
    }

    public @NotNull Version getChartVersion() {
        return chartVersion;
    }

    @Override
    public @NotNull String toString() {
        return "Chart{name='%s', description='%s', appVersion='%s', version=%s}".formatted(name,
                description,
                appVersion, chartVersion);
    }

    public static @NotNull Chart of(final @NotNull SearchResult result) {
        final var chart = new Chart();
        chart.name = result.getName();
        chart.description = result.getDescription();
        chart.appVersion = result.getAppVersion();
        chart.chartVersion = Version.parse(result.getChartVersion());
        return chart;
    }
}
