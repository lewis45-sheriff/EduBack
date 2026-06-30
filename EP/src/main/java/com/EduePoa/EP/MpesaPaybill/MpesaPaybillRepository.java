package com.EduePoa.EP.MpesaPaybill;

import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaPaybillRepository extends TenantAwareRepository<MpesaPaybill, Long> {

}
