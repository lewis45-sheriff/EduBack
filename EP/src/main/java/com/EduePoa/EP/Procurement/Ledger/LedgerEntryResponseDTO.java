package com.EduePoa.EP.Procurement.Ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponseDTO {
    private Long id;
    private String transactionType;
    private BigDecimal amount;
    private BigDecimal runningBalance;
    private String description;
    private String referenceType;
    private Long referenceId;
    private String referenceNumber;
    private LocalDate transactionDate;
    private String createdBy;
    private LocalDateTime createdAt;
}
