package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Optional identification mapping for transport boarding: maps an opaque device/SDK token to a
 * student. The same table maps biometric tokens, NFC card UIDs and QR identifiers — all treated as
 * sensitive opaque identifiers.
 *
 * <p><strong>Privacy:</strong> never store raw fingerprints, fingerprint/facial images, raw facial
 * templates or raw sensor data. Only the opaque token produced by the approved device/SDK is stored.
 *
 * <p>{@code biometricId} is unique within a tenant ({@code uk_biometric_token}, which also spans
 * {@code tenant_id}) so one token can never be mapped to more than one student in the same tenant.
 * A student may hold multiple tokens for different methods/devices; the (student, method, token)
 * combination stays unambiguous.
 */
@Entity
@Table(
        name = "student_transport_biometric",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_biometric_token",
                columnNames = {"biometric_id", "tenant_id"}
        ),
        indexes = {
                @Index(name = "idx_biometric_student", columnList = "student_id"),
                @Index(name = "idx_biometric_token", columnList = "biometric_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class StudentTransportBiometric extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    /**
     * Opaque identification token (biometric template id / NFC card UID / QR identifier).
     * Never a raw biometric image or template.
     */
    @Column(name = "biometric_id", nullable = false)
    private String biometricId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaptureMethod method;

    @Column(name = "device_vendor")
    private String deviceVendor;

    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private LocalDateTime enrolledAt;
}
