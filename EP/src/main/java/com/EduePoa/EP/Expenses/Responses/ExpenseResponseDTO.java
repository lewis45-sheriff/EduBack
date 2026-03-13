package com.EduePoa.EP.Expenses.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseResponseDTO {

    private Long id;
    private String expenseNumber;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String category;
    private String paymentMethod;
    private String description;
    private String vendorName;
    private String receiptNumber;
    private String referenceNumber;
    private String status;
    private String notes;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
