package com.EduePoa.EP.Grade.Stream;

import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Staff.Staff;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Where;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(
        name = "grade_stream",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_grade_stream_name",
                columnNames = {"grade_id", "name", "tenant_id"}
        )
)
@Where(clause = "deleted_flag = 'N'")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class GradeStream extends TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "grade_id", referencedColumnName = "id", nullable = false)
    private Grade grade;

    /**
     * The class teacher who owns this class/stream. Optional; at most one per stream.
     * Points to a {@link Staff} record (a teacher), not directly to a login user.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "class_teacher_id", referencedColumnName = "id")
    private Staff classTeacher;

    @JsonIgnore
    private char deletedFlag = 'N';
}
