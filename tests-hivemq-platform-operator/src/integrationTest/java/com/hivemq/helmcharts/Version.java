package com.hivemq.helmcharts;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

public record Version(int major, int minor, int patch) implements Comparable<Version> {

    @Override
    public int compareTo(final @NotNull Version other) {
        final var majorCompare = Integer.compare(this.major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        final var minorCompare = Integer.compare(this.minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public boolean equals(final @Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (Version) o;
        if (major != that.major) {
            return false;
        }
        if (minor != that.minor) {
            return false;
        }
        return patch == that.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public @NotNull String toString() {
        return String.format("%s.%s.%s", major, minor, patch);
    }

    static class Deserializer extends JsonDeserializer<Version> {

        @Override
        public @NotNull Version deserialize(final @NotNull JsonParser parser, final @NotNull DeserializationContext ctx)
                throws IOException {
            final var versionString = parser.getValueAsString();
            final var versionParts = versionString.split("\\.");
            if (versionParts.length != 3) {
                throw new IllegalArgumentException(String.format("Invalid version format: %s", versionString));
            }
            final var major = Integer.parseInt(versionParts[0]);
            final var minor = Integer.parseInt(versionParts[1]);
            final var patch = Integer.parseInt(versionParts[2]);
            return new Version(major, minor, patch);
        }
    }
}
