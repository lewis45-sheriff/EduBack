package com.EduePoa.EP.FeeComponents;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;

import java.util.Optional;

public interface FeeComponentsRepository extends TenantAwareRepository<FeeComponents, Long> {
    Optional<FeeComponents> findByName(String name);


}
