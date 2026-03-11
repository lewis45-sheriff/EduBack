package com.EduePoa.EP.Procurement.Ledger;


import com.EduePoa.EP.Authentication.Enum.TransactionType;
import com.EduePoa.EP.Authentication.User.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface LedgerService {
    void recordCredit(BigDecimal amount, String description, Long referenceId, String referenceNumber, LocalDate transactionDate, User createdBy);
    void recordDebit(BigDecimal amount, String description, Long referenceId, String referenceNumber, LocalDate transactionDate, User createdBy);
    Page<LedgerEntryResponseDTO> getLedgerEntries(TransactionType type, String referenceType, LocalDate startDate, LocalDate endDate, Pageable pageable);
    LedgerSummaryDTO getLedgerSummary(LocalDate startDate, LocalDate endDate);
}
