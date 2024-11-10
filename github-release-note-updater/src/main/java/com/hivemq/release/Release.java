package com.hivemq.release;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

record Release(String name, String tagName, ZonedDateTime publishedAt) {

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    @JsonCreator
    Release(
            @JsonProperty(value = "name") final @NotNull String name,
            @JsonProperty(value = "tagName") final @NotNull String tagName,
            @JsonProperty(value = "publishedAt") final @NotNull String publishedAt) {
        this(name, tagName, Instant.parse(publishedAt).atZone(ZONE_ID).toLocalDate().atStartOfDay(ZONE_ID));
    }
}
