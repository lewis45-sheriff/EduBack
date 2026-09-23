package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Grade.Stream.GradeStream;
import com.EduePoa.EP.Multitenancy.base.TenantScopedEntity;
import com.EduePoa.EP.Staff.Staff;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Where(clause = "deleted_flag = 'N'")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId AND tenant_id IS NOT NULL AND tenant_id != ''")
public class Grade extends TenantScopedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    private Integer gradeNumber;

    @Column
    private String name;

    @JsonIgnore
    private char deletedFlag = 'N';

    @OneToMany(mappedBy = "grade", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<GradeStream> gradeStreams = new ArrayList<>();

    /**
     * Class teacher for a grade that has no streams. When a grade is streamed,
     * the class teacher is assigned per {@link GradeStream} instead. Optional.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "class_teacher_id", referencedColumnName = "id")
    private Staff classTeacher;
}
