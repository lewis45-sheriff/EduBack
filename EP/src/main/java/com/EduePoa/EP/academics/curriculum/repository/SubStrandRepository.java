package com.EduePoa.EP.academics.curriculum.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.curriculum.entity.Strand;
import com.EduePoa.EP.academics.curriculum.entity.SubStrand;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubStrandRepository extends TenantAwareRepository<SubStrand, Long> {
    List<SubStrand> findByStrandOrderBySequenceAsc(Strand strand);
    Optional<SubStrand> findByStrandAndCode(Strand strand, String code);
    boolean existsByStrandAndCode(Strand strand, String code);
}
