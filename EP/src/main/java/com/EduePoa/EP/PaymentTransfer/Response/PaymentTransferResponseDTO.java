package com.EduePoa.EP.PaymentTransfer.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.FinanceTransaction.FinanceTransaction;
import com.EduePoa.EP.PaymentTransfer.PaymentTransferStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentTransferResponseDTO {
    private Long id;
    private PaymentTransferStatus status;

    private String sourcePaymentReference;
    private Long sourceTransactionId;
    private BigDecimal originalPaymentAmount;
    private BigDecimal amount;
    /** How much of the source payment is still transferable after this request. */
    private BigDecimal remainingTransferableAfter;

    private Long sourceStudentId;
    private String sourceStudentName;
    private Long sourceInvoiceId;

    private Long destStudentId;
    private String destStudentName;
    private Long destInvoiceId;

    private Term term;
    private Year academicYear;
    private FinanceTransaction.PaymentMethod paymentMethod;
    private String reason;

    private String createdByName;
    private LocalDateTime createdAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private LocalDateTime appliedAt;
    private String rejectionReason;
}
