package com.EduePoa.EP.TransactionReversal.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.TransactionReversal.TransactionReversalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;

@Data
@Builder
public class ReversalResponseDTO {
    private Long id;
    private Long financeTransactionId;
    private Long studentId;
    private String studentName;
    private Long invoiceId;
    private FinanceTransaction.TransactionType transactionType;
    private String category;
    private BigDecimal amount;
    private String transactionReference;
    private Term term;
    private Year academicYear;
    private boolean transferOriginated;
    private Long paymentTransferId;
    private String reason;
    private TransactionReversalStatus status;
    private String createdByName;
    private LocalDateTime createdAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime appliedAt;
    private String rejectionReason;
}
