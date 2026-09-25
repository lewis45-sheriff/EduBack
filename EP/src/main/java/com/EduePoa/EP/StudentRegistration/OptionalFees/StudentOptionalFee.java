package com.EduePoa.EP.StudentRegistration.OptionalFees;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;

/**
 * An optional fee-structure line item ({@link FeeComponentConfig}) assigned to a
 * specific {@link Student} for a specific term and academic year.
 * <p>
 * The {@link #amount} is a snapshot of the fee-structure line-item amount at the
 * time of assignment and is the authoritative amount for this assignment; later
 * changes to the fee structure do not affect existing assignments. Assignments
 * are soft deleted via {@link #isDeleted} so that history is preserved and a
 * removed assignment can be recreated.
 */
@Entity
@Table(
        name = "student_optional_fee",
        indexes = {
                @Index(name = "idx_sof_student_term_year",
                        columnList = "student_id, term, academic_year, is_deleted")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class StudentOptionalFee extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_component_config_id", nullable = false)
    private FeeComponentConfig feeComponentConfig;

    /**
     * Snapshot of the fee-structure line-item amount at assignment time.
     * Authoritative for this assignment; never recomputed from the fee structure
     * afterwards.
     */
    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Term term;

    @Column(name = "academic_year", nullable = false)
    private Year academicYear;

    /** Whether the assignment was created by an admin or a parent. */
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_by", nullable = false, length = 20)
    private AssignedBy assignedBy;

    /** Id of the authenticated user who created the assignment (server-resolved). */
    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @CreationTimestamp
    @Column(name = "assigned_at", updatable = false)
    private LocalDateTime assignedAt;

    /**
     * Whether this assignment has already been incorporated into a generated
     * invoice. Once invoiced, the assignment must not be physically removed in a
     * way that would silently change historical invoice totals.
     */
    @Builder.Default
    @Column(name = "is_invoiced", nullable = false)
    private char isInvoiced = 'N';

    /** Soft-delete flag: 'N' active, 'Y' removed. Matches the project convention. */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private char isDeleted = 'N';
}
