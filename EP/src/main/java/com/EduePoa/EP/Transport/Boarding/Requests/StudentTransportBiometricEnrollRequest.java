package com.EduePoa.EP.Transport.Boarding.Requests;

import com.EduePoa.EP.Transport.Boarding.CaptureMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enrollment of an optional transport identification token (biometric / NFC card / QR) for a
 * student. {@code MANUAL} is never accepted here — that is validated in the service layer.
 *
 * <p>The external contract uses {@code biometricId}; {@code identifierToken} is accepted as the
 * preferred neutral alias.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentTransportBiometricEnrollRequest {

    @NotNull(message = "studentId is required")
    private Long studentId;

    private String biometricId;

    private String identifierToken;

    @NotNull(message = "method is required")
    private CaptureMethod method;

    private String deviceVendor;

    public String resolveToken() {
        String token = identifierToken != null ? identifierToken : biometricId;
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
