package com.hivemq.release;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record IndexFile(String apiVersion, Map<String, List<Chart>> entries, String generated) {

    @JsonCreator
    IndexFile(
            @JsonProperty("apiVersion") final @NotNull String apiVersion,
            @JsonProperty("entries") final @NotNull Map<String, List<Chart>> entries,
            @JsonProperty("generated") final @NotNull String generated) {
        this.apiVersion = apiVersion;
        this.entries = entries;
        this.generated = generated;
    }
}
