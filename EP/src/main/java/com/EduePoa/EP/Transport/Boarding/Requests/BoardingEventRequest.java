package com.EduePoa.EP.Transport.Boarding.Requests;

import com.EduePoa.EP.Transport.Boarding.BoardingLeg;
import com.EduePoa.EP.Transport.Boarding.CaptureMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One boarding event within a scan batch.
 *
 * <p>Exactly one student identifier must be supplied: {@code studentId}, {@code admissionNumber},
 * or {@code identifierToken}. The external contract also accepts {@code biometricId} as an alias of
 * {@code identifierToken} for backward compatibility; the neutral name is preferred internally
 * because NFC/QR tokens are not biometric data. Cross-field validation (exactly-one identifier,
 * identifier-vs-method consistency) is performed in the service layer so that a single malformed
 * event is reported inside {@code rejected} rather than failing the whole batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardingEventRequest {

    /** Client-generated idempotency key (optional but strongly recommended for offline sync). */
    private String clientEventId;

    private Long studentId;

    private String admissionNumber;

    /** Neutral name for the opaque biometric/NFC/QR token. */
    private String identifierToken;

    /** External-contract alias of {@link #identifierToken}. */
    private String biometricId;

    @NotNull(message = "vehicleId is required")
    private Long vehicleId;

    @NotNull(message = "leg is required")
    private BoardingLeg leg;

    @NotNull(message = "method is required")
    private CaptureMethod method;

    @NotBlank(message = "capturedAt is required")
    private String capturedAt;

    private String deviceId;

    @DecimalMin(value = "-90.0", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "longitude must be between -180 and 180")
    private Double longitude;

    /**
     * Resolves the effective token from either the neutral field or the external alias.
     */
    public String resolveToken() {
        String token = identifierToken != null ? identifierToken : biometricId;
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String resolveAdmissionNumber() {
        if (admissionNumber == null) {
            return null;
        }
        String trimmed = admissionNumber.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
