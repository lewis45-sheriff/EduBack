package com.EduePoa.EP.academics.assessment.repository;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.academics.assessment.entity.RankingPolicy;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RankingPolicyRepository extends TenantAwareRepository<RankingPolicy, Long> {
    List<RankingPolicy> findByEnabledTrue();
    List<RankingPolicy> findByGradeId(Long gradeId);
}
