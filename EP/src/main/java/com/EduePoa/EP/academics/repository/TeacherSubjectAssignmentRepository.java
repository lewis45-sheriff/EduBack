package com.EduePoa.EP.academics.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.entity.TeacherSubjectAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.List;

@Repository
public interface TeacherSubjectAssignmentRepository extends TenantAwareRepository<TeacherSubjectAssignment, Long> {

    List<TeacherSubjectAssignment> findByTeacherIdAndYear(Long teacherId, Year year);

    List<TeacherSubjectAssignment> findByGradeIdAndYear(Long gradeId, Year year);

    List<TeacherSubjectAssignment> findByAcademicSubjectIdAndYear(Long academicSubjectId, Year year);

    /**
     * True if the same assignment already exists (used for idempotency and duplicate rejection).
     * Handles nullable gradeStream/term with null-safe comparisons.
     */
    @Query("""
           select count(a) > 0 from TeacherSubjectAssignment a
           where a.teacher.id = :teacherId
             and a.grade.id = :gradeId
             and a.academicSubject.id = :subjectId
             and a.year = :year
             and ((:streamId is null and a.gradeStream is null) or a.gradeStream.id = :streamId)
             and ((:term is null and a.term is null) or a.term = :term)
           """)
    boolean existsAssignment(@Param("teacherId") Long teacherId,
                             @Param("gradeId") Long gradeId,
                             @Param("subjectId") Long subjectId,
                             @Param("streamId") Long streamId,
                             @Param("year") Year year,
                             @Param("term") com.EduePoa.EP.Authentication.Enum.Term term);

    /**
     * Authorization check: is this teacher assigned to this subject for this grade in this year?
     * A stream-agnostic (null stream) or matching-stream assignment both authorize.
     */
    @Query("""
           select count(a) > 0 from TeacherSubjectAssignment a
           where a.teacher.id = :teacherId
             and a.grade.id = :gradeId
             and a.academicSubject.id = :subjectId
             and a.year = :year
             and a.active = true
           """)
    boolean isTeacherAuthorized(@Param("teacherId") Long teacherId,
                                @Param("gradeId") Long gradeId,
                                @Param("subjectId") Long subjectId,
                                @Param("year") Year year);
}
