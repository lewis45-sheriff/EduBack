package com.EduePoa.EP.HR.proration.repository;

import com.EduePoa.EP.HR.proration.entity.ProrationPolicy;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ProrationPolicyRepository extends TenantAwareRepository<ProrationPolicy, Long> {

    /** The policy in force on {@code asOf}: latest effective-dated row on or before that date. */
    Optional<ProrationPolicy> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
            LocalDate asOf, Limit limit);

    /** The highest version number so a new policy can increment it. */
    Optional<ProrationPolicy> findFirstByOrderByVersionDesc();

    boolean existsByVersion(Integer version);
}
