package com.EduePoa.EP.Expenses.Requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseRequestDTO {

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
}
