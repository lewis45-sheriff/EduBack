package com.EduePoa.EP.HR.config.repository;

import com.EduePoa.EP.HR.config.entity.HousingLevyConfig;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface HousingLevyConfigRepository extends TenantAwareRepository<HousingLevyConfig, Long> {

    Optional<HousingLevyConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            LocalDate asOf, Limit limit);

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);
}
