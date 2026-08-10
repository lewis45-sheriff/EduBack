package com.EduePoa.EP.FeeStructure.FeeComponentConfig;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FeeComponentConfigRepository extends TenantAwareRepository<FeeComponentConfig, Long> {
    Optional<FeeComponentConfig> findByName(String name);
    // In your repository
    @Query("SELECT f FROM FeeComponentConfig f WHERE f.name = :name AND f.term = :term AND f.feeStructure.id = :feeStructureId")
    Optional<FeeComponentConfig> findByNameAndTermAndFeeStructureId(
            @Param("name") String name,
            @Param("term") String term,
            @Param("feeStructureId") Long feeStructureId);
}
