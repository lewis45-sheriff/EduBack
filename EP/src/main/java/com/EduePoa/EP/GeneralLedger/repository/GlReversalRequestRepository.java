package com.EduePoa.EP.GeneralLedger.repository;

import com.EduePoa.EP.GeneralLedger.entity.GlReversalRequest;
import com.EduePoa.EP.GeneralLedger.enums.GlReversalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GlReversalRequestRepository extends JpaRepository<GlReversalRequest, Long> {

    boolean existsByTargetJournalIdAndStatusIn(Long targetJournalId, List<GlReversalStatus> statuses);

    List<GlReversalRequest> findAllByOrderByCreatedAtDesc();

    List<GlReversalRequest> findByStatusOrderByCreatedAtDesc(GlReversalStatus status);
}
