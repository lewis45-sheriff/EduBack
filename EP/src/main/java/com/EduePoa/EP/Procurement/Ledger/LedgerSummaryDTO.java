package com.EduePoa.EP.Procurement.Ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerSummaryDTO {

    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netBalance;
    private LocalDate startDate;
    private LocalDate endDate;
}
