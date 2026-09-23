package com.EduePoa.EP.Grade.Stream;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeStreamRepository extends TenantAwareRepository<GradeStream, Long> {
    List<GradeStream> findByGradeId(Long gradeId);

    Optional<GradeStream> findByGradeIdAndName(Long gradeId, String name);

    Optional<GradeStream> findByClassTeacherId(Long classTeacherId);
}
