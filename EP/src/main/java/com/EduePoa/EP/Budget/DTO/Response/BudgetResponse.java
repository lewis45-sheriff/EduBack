package com.EduePoa.EP.Budget.DTO.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Budget.Enum.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetResponse {
    private Long id;
    private String name;
    private String description;
    private Term academicTerm;
    private Year academicYear;
    private LocalDate startDate;
    private LocalDate endDate;
    private BudgetStatus status;
    private BigDecimal totalIncomeAllocated;
    private BigDecimal totalExpenseAllocated;
    private List<BudgetLineResponse> lines;
    private String createdByName;
    private String approvedByName;
    private String closedByName;
    private String approvalComment;
    private String closureComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime closedAt;
}
