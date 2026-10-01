package com.EduePoa.EP.Transport.Boarding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a boarding event / identification token was captured.
 *
 * <p>{@link #MANUAL} is the mandatory baseline and must function with no biometric, card, or QR
 * configuration. The remaining methods are optional identification layers.
 */
public enum CaptureMethod {
    MANUAL,
    BIOMETRIC,
    NFC_CARD,
    QR;

    @JsonCreator
    public static CaptureMethod fromJson(String value) {
        CaptureMethod method = fromString(value);
        if (method == null) {
            throw new IllegalArgumentException(
                    "Invalid method '" + value + "'. Expected one of MANUAL, BIOMETRIC, NFC_CARD, QR.");
        }
        return method;
    }

    @JsonValue
    public String toJson() {
        return name();
    }

    /**
     * Best-effort case- and separator-insensitive parse.
     *
     * @param value raw string (may be null)
     * @return the matching {@link CaptureMethod}, or {@code null} if it cannot be resolved
     */
    public static CaptureMethod fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replaceAll("[^A-Z]", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.contains("MANUAL")) {
            return MANUAL;
        }
        if (normalized.contains("BIOMETRIC") || normalized.contains("BIO")) {
            return BIOMETRIC;
        }
        if (normalized.contains("NFC") || normalized.contains("CARD")) {
            return NFC_CARD;
        }
        if (normalized.contains("QR")) {
            return QR;
        }
        return null;
    }

    /**
     * Whether this method relies on an opaque identification token (biometric/card/QR)
     * rather than a directly supplied student id / admission number.
     */
    public boolean requiresToken() {
        return this == BIOMETRIC || this == NFC_CARD || this == QR;
    }
}
