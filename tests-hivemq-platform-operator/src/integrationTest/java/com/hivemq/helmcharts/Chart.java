package com.hivemq.helmcharts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Chart {

    @JsonProperty("name")
    private @Nullable String name;

    @JsonProperty("description")
    private @Nullable String description;

    @JsonProperty("appVersion")
    private @Nullable String appVersion;

    @JsonProperty("version")
    @JsonDeserialize(using = Version.Deserializer.class)
    private @Nullable Version version;

    public @Nullable String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable String getAppVersion() {
        return appVersion;
    }

    public @Nullable Version getVersion() {
        return version;
    }

    @Override
    public @NotNull String toString() {
        return "Chart{" +
                "name='" +
                name +
                '\'' +
                ", description='" +
                description +
                '\'' +
                ", appVersion='" +
                appVersion +
                '\'' +
                ", version=" +
                version +
                '}';
    }
}
