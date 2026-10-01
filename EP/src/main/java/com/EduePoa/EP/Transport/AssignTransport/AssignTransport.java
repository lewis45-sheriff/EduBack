package com.EduePoa.EP.Transport.AssignTransport;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import com.EduePoa.EP.Transport.TransportType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;

@Entity
@Table(
        name = "student_transport",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_term_year",
                columnNames = {"student_id", "term", "year"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class AssignTransport extends TenantScopedEntity {

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

    @Column(name = "pickup_location", nullable = false)
    private String pickupLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false)
    private TransportType transportType;

    /**
     * Academic term this assignment applies to. Nullable at the DB level so pre-existing
     * (pre per-term) rows are not invalidated during schema update; required for new assignments.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "term")
    private Term term;

    /**
     * Calendar year for {@link #term}. Nullable for backward compatibility; required for new assignments.
     */
    @Column(name = "year")
    private Integer year;

    @Column(name = "assignment_date", nullable = false)
    private LocalDate assignmentDate;
}
