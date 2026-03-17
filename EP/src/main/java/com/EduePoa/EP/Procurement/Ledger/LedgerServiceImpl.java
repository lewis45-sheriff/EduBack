package com.EduePoa.EP.Procurement.Ledger;


import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.TransactionType;
import com.EduePoa.EP.Authentication.User.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCredit(BigDecimal amount, String description, Long referenceId, String referenceNumber, LocalDate transactionDate, User createdBy) {
        createEntry(TransactionType.CREDIT, amount, description,
                "FEE_PAYMENT", referenceId, referenceNumber,
                transactionDate, createdBy);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDebit(BigDecimal amount, String description, Long referenceId, String referenceNumber, LocalDate transactionDate, User createdBy) {
        createEntry(TransactionType.DEBIT, amount, description,
                "SUPPLIER_PAYMENT", referenceId, referenceNumber,
                transactionDate, createdBy);
    }

    private void createEntry(TransactionType type, BigDecimal amount, String description, String referenceType, Long referenceId, String referenceNumber, LocalDate transactionDate, User createdBy) {
        Optional<LedgerEntry> existing = ledgerEntryRepository
                .findByReferenceTypeAndReferenceId(referenceType, referenceId)
                .filter(entry -> entry.getReferenceType() != null && entry.getReferenceId() != null);
        if (existing.isPresent()) {
            log.warn("Ledger entry already exists for {} with ID {}. Skipping.", referenceType, referenceId);
//            auditService.logAction("SKIP", "LEDGER", existing.get(), "Duplicate ledger entry skipped for " + referenceType + " #" + referenceId);
            return;
        }

        // Calculate running balance
        BigDecimal runningBalance = calculateRunningBalance(type, amount);
        LedgerEntry entry = LedgerEntry.builder()
                .transactionType(type)
                .amount(amount)
                .runningBalance(runningBalance)
                .description(description)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .referenceNumber(referenceNumber)
                .transactionDate(transactionDate)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();

        LedgerEntry savedEntry = ledgerEntryRepository.save(entry);
//        auditService.logAction("POST", "LEDGER", savedEntry,
//                String.format("Ledger %s entry recorded: %s for %s (ref: %s #%d)",
//                        type, amount, referenceType, referenceNumber, referenceId));
        log.info("Ledger {} entry recorded: {} {} (ref: {} #{})",
                type, amount, referenceType, referenceNumber, referenceId);
    }

    private BigDecimal calculateRunningBalance(TransactionType type, BigDecimal amount) {
        Optional<LedgerEntry> lastEntry = ledgerEntryRepository.findTopByOrderByIdDesc();
        BigDecimal previousBalance = lastEntry.map(LedgerEntry::getRunningBalance).orElse(BigDecimal.ZERO);

        if (type == TransactionType.CREDIT) {
            return previousBalance.add(amount);
        } else {
            return previousBalance.subtract(amount);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponseDTO> getLedgerEntries(
            TransactionType type, String referenceType,
            LocalDate startDate, LocalDate endDate, Pageable pageable) {

        Page<LedgerEntry> entries;

        if (type != null && startDate != null && endDate != null) {
            entries = ledgerEntryRepository.findByTransactionTypeAndTransactionDateBetween(
                    type, startDate, endDate, pageable);
        } else if (type != null) {
            entries = ledgerEntryRepository.findByTransactionType(type, pageable);
        } else if (referenceType != null) {
            entries = ledgerEntryRepository.findByReferenceType(referenceType, pageable);
        } else if (startDate != null && endDate != null) {
            entries = ledgerEntryRepository.findByTransactionDateBetween(startDate, endDate, pageable);
        } else {
            entries = ledgerEntryRepository.findAll(pageable);
        }

//        auditService.logAction("GET", "LEDGER", null, String.format("Ledger entries retrieved: page %d, size %d, total %d",
//                pageable.getPageNumber(), pageable.getPageSize(), entries.getTotalElements()));
        return entries.map(this::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerSummaryDTO getLedgerSummary(LocalDate startDate, LocalDate endDate) {
        BigDecimal totalCredits;
        BigDecimal totalDebits;

        if (startDate != null && endDate != null) {
            totalCredits = ledgerEntryRepository.sumCreditsByDateRange(startDate, endDate);
            totalDebits = ledgerEntryRepository.sumDebitsByDateRange(startDate, endDate);
        } else {
            totalCredits = ledgerEntryRepository.sumAllCredits();
            totalDebits = ledgerEntryRepository.sumAllDebits();
        }

        BigDecimal netBalance = totalCredits.subtract(totalDebits);

        LedgerSummaryDTO summary = LedgerSummaryDTO.builder()
                .totalCredits(totalCredits)
                .totalDebits(totalDebits)
                .netBalance(netBalance)
                .startDate(startDate)
                .endDate(endDate)
                .build();

//        auditService.logAction("GET", "LEDGER", null, String.format("Ledger summary retrieved: credits=%s, debits=%s, net=%s",
//                totalCredits, totalDebits, netBalance));
        return summary;
    }

    private LedgerEntryResponseDTO toResponseDTO(LedgerEntry entry) {
        return LedgerEntryResponseDTO.builder()
                .id(entry.getId())
                .transactionType(entry.getTransactionType().name())
                .amount(entry.getAmount())
                .runningBalance(entry.getRunningBalance())
                .description(entry.getDescription())
                .referenceType(entry.getReferenceType())
                .referenceId(entry.getReferenceId())
                .referenceNumber(entry.getReferenceNumber())
                .transactionDate(entry.getTransactionDate())
                .createdBy(entry.getCreatedBy() != null ? entry.getCreatedBy().getEmail() : null)
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
