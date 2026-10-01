package com.EduePoa.EP.Transport.TransportTransactions.Approval;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Multitenancy.repository.TenantAwareRepository;
import com.EduePoa.EP.Transport.TransportType;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingTransportTransactionRepository
        extends TenantAwareRepository<PendingTransportTransaction, Long> {

    List<PendingTransportTransaction> findAllByOrderByCreatedAtDesc();

    List<PendingTransportTransaction> findByStatusOrderByCreatedAtDesc(PendingTransportTransactionStatus status);

    /**
     * Guards against stacking multiple pending requests for the same student/vehicle/term/year/type,
     * which would otherwise let two approvals both post against the same balance.
     */
    boolean existsByStudentIdAndVehicleIdAndTermAndYearAndTransportTypeAndStatus(
            Long studentId, Long vehicleId, Term term, Integer year,
            TransportType transportType, PendingTransportTransactionStatus status);
}
