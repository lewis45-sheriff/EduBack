package com.EduePoa.EP.Transport.Boarding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The four transport legs a student may be recorded on for a single service date.
 *
 * <pre>
 * MORNING_PICKUP   Student boards the vehicle at the morning pickup stop and travels toward school
 * MORNING_DROPOFF  Student alights from the vehicle at school
 * EVENING_PICKUP   Student boards the vehicle at school and travels toward home
 * EVENING_DROPOFF  Student alights at the home/drop-off stop
 * </pre>
 *
 * Tolerant JSON parsing mirrors {@link com.EduePoa.EP.Transport.TransportType}: canonical names
 * ("MORNING_PICKUP") as well as common client variants ("morning_pickup", "Morning Pickup",
 * "morningPickup") are accepted; the canonical name is always serialized.
 */
public enum BoardingLeg {
    MORNING_PICKUP,
    MORNING_DROPOFF,
    EVENING_PICKUP,
    EVENING_DROPOFF;

    /**
     * The half of the day a leg belongs to. A ONE_WAY student rides in exactly one phase per day;
     * the first phase in which a boarding is recorded "claims" the day and locks out the other.
     */
    public enum Phase {
        MORNING,
        EVENING
    }

    /**
     * The {@link Phase} this leg belongs to: {@code MORNING_*} legs are {@link Phase#MORNING},
     * {@code EVENING_*} legs are {@link Phase#EVENING}.
     */
    public Phase phase() {
        return (this == MORNING_PICKUP || this == MORNING_DROPOFF) ? Phase.MORNING : Phase.EVENING;
    }

    @JsonCreator
    public static BoardingLeg fromJson(String value) {
        BoardingLeg leg = fromString(value);
        if (leg == null) {
            throw new IllegalArgumentException(
                    "Invalid leg '" + value + "'. Expected one of MORNING_PICKUP, MORNING_DROPOFF, "
                            + "EVENING_PICKUP, EVENING_DROPOFF.");
        }
        return leg;
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Best-effort case- and separator-insensitive parse.
     *
     * @param value raw string (may be null)
     * @return the matching {@link BoardingLeg}, or {@code null} if it cannot be resolved
     */
    public static BoardingLeg fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replaceAll("[^A-Z]", "");
        if (normalized.isEmpty()) {
            return null;
        }
        boolean morning = normalized.contains("MORNING");
        boolean evening = normalized.contains("EVENING");
        boolean pickup = normalized.contains("PICKUP");
        boolean dropoff = normalized.contains("DROPOFF") || normalized.contains("DROP");
        if (morning && pickup) {
            return MORNING_PICKUP;
        }
        if (morning && dropoff) {
            return MORNING_DROPOFF;
        }
        if (evening && pickup) {
            return EVENING_PICKUP;
        }
        if (evening && dropoff) {
            return EVENING_DROPOFF;
        }
        return null;
    }
}
