package com.EduePoa.EP.Transport.Boarding;

import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single student's attendance event for one transport leg on one school service date.
 *
 * <p>The business uniqueness rule — a student may have at most one event per (serviceDate, leg) —
 * is enforced by {@code uk_boarding_student_date_leg}. Because every row also carries a
 * {@code tenant_id} (from {@link TenantScopedEntity}) the constraint spans that column too, so two
 * different tenants can independently record the same student/date/leg combination. The unique
 * constraint is the ultimate protection against duplicate attendance; {@code clientEventId} is an
 * idempotency optimisation layered on top.
 */
@Entity
@Table(
        name = "transport_boarding_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_boarding_student_date_leg",
                columnNames = {"student_id", "service_date", "leg", "tenant_id"}
        ),
        indexes = {
                @Index(name = "idx_boarding_vehicle_date_leg",
                        columnList = "vehicle_id, service_date, leg"),
                @Index(name = "idx_boarding_client_event_id",
                        columnList = "client_event_id"),
                @Index(name = "idx_boarding_student_date",
                        columnList = "student_id, service_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class TransportBoardingEvent extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnore
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    @JsonIgnore
    private Transport vehicle;

    /**
     * School service date derived from {@link #capturedAt} using the school timezone.
     * Never trusted from the client directly.
     */
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BoardingLeg leg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaptureMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BoardingStatus status = BoardingStatus.ON_TIME;

    /**
     * Device/client capture time, normalized to the school timezone and stored as local date-time.
     * Represents when the student was actually scanned/marked.
     */
    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    /**
     * Server/database recording time. Never replaced by a client-supplied value.
     */
    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /**
     * Client-generated idempotency key (nullable). Used to short-circuit repeated offline
     * resubmission of the same event before the business uniqueness rule is even evaluated.
     */
    @Column(name = "client_event_id")
    private String clientEventId;

    /**
     * Username of the authenticated operator who recorded this event (from the security context at
     * persist time). Nullable for events recorded by system/automated flows or pre-existing rows.
     */
    @Column(name = "recorded_by")
    private String recordedBy;
}
