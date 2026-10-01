package com.EduePoa.EP.Transport.Boarding.Responses;

import com.EduePoa.EP.Transport.Boarding.CaptureMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Enrollment response. The token is returned masked (e.g. {@code AF93****92}); raw biometric
 * material is never stored and therefore never returned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentTransportBiometricResponse {

    private Long id;
    private Long studentId;

    /** Masked token, e.g. {@code AF93****92}. */
    private String biometricId;

    private CaptureMethod method;
    private LocalDateTime enrolledAt;
}
