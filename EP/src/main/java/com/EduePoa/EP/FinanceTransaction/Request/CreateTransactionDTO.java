package com.EduePoa.EP.FinanceTransaction.Request;

import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateTransactionDTO {
    private Long studentId;
    private String studentName;
    private String admissionNumber;
    private FinanceTransaction.TransactionType transactionType;
    private String category;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private String description;
    private FinanceTransaction.PaymentMethod paymentMethod;
    private String reference;

}
