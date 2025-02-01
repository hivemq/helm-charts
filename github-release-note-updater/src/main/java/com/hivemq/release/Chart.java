package com.hivemq.release;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.zafarkhaja.semver.Version;
import org.jetbrains.annotations.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
record Chart(String name, Version version, Version appVersion, String description) implements Comparable<Chart> {

    @JsonCreator
    Chart(
            @JsonProperty(value = "name") final @NotNull String name,
            @JsonProperty(value = "version") final @NotNull String version,
            @JsonProperty(value = "appVersion") final @NotNull String appVersion,
            @JsonProperty(value = "description") final @NotNull String description) {
        this(name, Version.parse(version), Version.parse(appVersion, false), description);
    }

    @Override
    public int compareTo(final @NotNull Chart o) {
        return version.compareTo(o.version);
    }
}
