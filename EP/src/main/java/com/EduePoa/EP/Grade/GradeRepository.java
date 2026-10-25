package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeRepository extends TenantAwareRepository<Grade, Long> {
    Optional<Grade> findByName( String Name);

    Optional<Grade> findByClassTeacherId(Long classTeacherId);

    /** All grades a staff member is the class teacher of (a teacher may own several). */
    List<Grade> findAllByClassTeacherId(Long classTeacherId);
}
