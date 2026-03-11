package com.EduePoa.EP.MpesaPaybill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaPaybillRepository extends JpaRepository<MpesaPaybill,Long> {

}
