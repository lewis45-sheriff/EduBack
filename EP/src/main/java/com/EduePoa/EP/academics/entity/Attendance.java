package com.EduePoa.EP.academics.entity;

import com.EduePoa.EP.Authentication.Enum.AttendanceStatus;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Daily student attendance record.
 * <p>
 * One record per student per calendar date (enforced by the unique
 * constraint on {@code student_id, date}). Tenant scoping is handled by
 * {@link TenantScopedEntity}; do not set {@code tenantId} manually.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "attendance", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id", "date" }))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Attendance extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "marked_by")
    private String markedBy;
}
