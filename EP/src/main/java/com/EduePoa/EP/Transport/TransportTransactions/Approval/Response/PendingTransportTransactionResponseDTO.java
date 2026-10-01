package com.EduePoa.EP.Transport.TransportTransactions.Approval.Response;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportTransactions.Approval.PendingTransportTransactionStatus;
import com.EduePoa.EP.Transport.TransportType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PendingTransportTransactionResponseDTO {
    private Long id;

    private Long studentId;
    private String studentName;
    private String admissionNumber;

    private Long vehicleId;
    private String vehicleNumber;
    private String route;

    private TransportType transportType;
    private Term term;
    private Integer year;

    private Double amount;
    private String paymentMethod;

    private Double expectedFee;
    private Double totalPaidBeforeSnapshot;
    private Double arrearsBeforeSnapshot;

    private String reason;
    private PendingTransportTransactionStatus status;

    private String createdByName;
    private LocalDateTime createdAt;

    private String approvedByName;
    private LocalDateTime approvedAt;

    /** Id of the posted TransportTransactions row, present once APPROVED. */
    private Long postedTransactionId;

    private String rejectionReason;
}
