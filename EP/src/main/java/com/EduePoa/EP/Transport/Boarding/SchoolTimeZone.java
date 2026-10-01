package com.EduePoa.EP.Transport.Boarding;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Single source of truth for the application's school timezone.
 *
 * <p>The codebase previously relied on the JVM default zone; boarding tracking requires a stable,
 * explicit zone so that a client capture timestamp such as {@code 2026-09-29T06:42:11+03:00}
 * always resolves to the correct local service date. Do not scatter {@code ZoneId.of(...)}
 * elsewhere — reuse this utility.
 */
public final class SchoolTimeZone {

    public static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");

    private SchoolTimeZone() {
    }

    /**
     * Parses an offset-aware client timestamp (e.g. {@code 2026-09-29T06:42:11+03:00}), converts it
     * to the school timezone and returns the zone-local {@link LocalDateTime} suitable for storage.
     * If the supplied value carries no offset it is interpreted as already being in the school zone.
     *
     * @param raw the raw timestamp string
     * @return the normalized local date-time in the school timezone
     * @throws DateTimeParseException if the value cannot be parsed as a date-time
     */
    public static LocalDateTime normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new DateTimeParseException("capturedAt is required", String.valueOf(raw), 0);
        }
        String value = raw.trim();
        try {
            // Offset-aware, e.g. 2026-09-29T06:42:11+03:00 or ...Z
            OffsetDateTime odt = OffsetDateTime.parse(value);
            return odt.atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through to offset-less parsing.
        }
        // No offset supplied: treat as already local to the school zone.
        LocalDateTime ldt = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ldt;
    }

    /**
     * Derives the school-local service date for an already-normalized local date-time.
     */
    public static LocalDate serviceDateOf(LocalDateTime normalizedLocal) {
        return normalizedLocal.toLocalDate();
    }

    /**
     * Convenience: normalize a raw client timestamp and return the school-local service date.
     */
    public static LocalDate serviceDateFromRaw(String raw) {
        return normalize(raw).toLocalDate();
    }

    /**
     * Current date in the school timezone.
     */
    public static LocalDate today() {
        return ZonedDateTime.now(ZONE).toLocalDate();
    }
}
