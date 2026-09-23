package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GradeRepository extends TenantAwareRepository<Grade, Long> {
    Optional<Grade> findByName( String Name);

    Optional<Grade> findByClassTeacherId(Long classTeacherId);
}
