package com.EduePoa.EP.HR.config.repository;

import com.EduePoa.EP.HR.config.entity.ShifConfig;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ShifConfigRepository extends TenantAwareRepository<ShifConfig, Long> {

    Optional<ShifConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            LocalDate asOf, Limit limit);

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);
}
