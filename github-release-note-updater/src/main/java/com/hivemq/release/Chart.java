package com.hivemq.release;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.zafarkhaja.semver.Version;
import org.jetbrains.annotations.NotNull;

record Chart(String name, Version version, Version appVersion, String description) implements Comparable<Chart> {

    @JsonCreator
    Chart(
            @JsonProperty(value = "name") final @NotNull String name,
            @JsonProperty(value = "version") final @NotNull String version,
            @JsonProperty(value = "app_version") final @NotNull String appVersion,
            @JsonProperty(value = "description") final @NotNull String description) {
        this(name.split("/")[1], Version.parse(version), Version.parse(appVersion), description);
    }

    @Override
    public int compareTo(final @NotNull Chart o) {
        return version.compareTo(o.version);
    }
}
