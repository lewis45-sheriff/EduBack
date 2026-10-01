package com.EduePoa.EP.Transport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Direction of a student's transport usage.
 * Replaces the previous free-text {@code transportType} strings ("ONE_WAY" / "TWO_WAY")
 * that were matched heuristically in multiple places.
 */
public enum TransportType {
    ONE_WAY,
    TWO_WAY;

    /**
     * Tolerant JSON deserialization. Accepts the canonical names ("ONE_WAY", "TWO_WAY") as well
     * as common variants sent by clients, e.g. "OneWay", "oneWay", "one way", "ONEWAY",
     * "TwoWay", "two_way", etc. Unknown values raise a clear error.
     *
     * @param value the raw JSON string
     * @return the matching {@link TransportType}
     * @throws IllegalArgumentException if the value cannot be resolved
     */
    @JsonCreator
    public static TransportType fromJson(String value) {
        TransportType type = fromString(value);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Invalid transportType '" + value + "'. Expected ONE_WAY or TWO_WAY.");
        }
        return type;
    }

    /**
     * Canonical value used when serializing to JSON responses.
     */
    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Best-effort parse of a free-text transport type.
     * Accepts values like "ONE_WAY", "OneWay", "one way", "ONEWAY", "TWO_WAY", "TwoWay",
     * "two way", "TWOWAY". Case- and separator-insensitive.
     *
     * @param value the raw string (may be null)
     * @return the matching {@link TransportType}, or {@code null} if it cannot be resolved
     */
    public static TransportType fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.contains("ONE")) {
            return ONE_WAY;
        }
        if (normalized.contains("TWO")) {
            return TWO_WAY;
        }
        return null;
    }
}
