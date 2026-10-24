package com.EduePoa.EP.HR.config.repository;

import com.EduePoa.EP.HR.config.entity.PayeBand;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PayeBandRepository extends TenantAwareRepository<PayeBand, Long> {

   
    @Query("select max(b.effectiveFrom) from PayeBand b where b.effectiveFrom <= :asOf")
    LocalDate findLatestEffectiveFromOnOrBefore(@Param("asOf") LocalDate asOf);

    List<PayeBand> findByEffectiveFromOrderBySequenceAsc(LocalDate effectiveFrom);

    boolean existsByEffectiveFrom(LocalDate effectiveFrom);

    List<PayeBand> findAllByOrderByEffectiveFromDescSequenceAsc();
}
