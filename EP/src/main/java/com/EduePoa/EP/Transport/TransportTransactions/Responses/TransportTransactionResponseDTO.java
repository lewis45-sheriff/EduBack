package com.EduePoa.EP.Transport.TransportTransactions.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TransportTransactionResponseDTO {
    private Long id;
    private Double amount;
    private String paymentMethod;
    private Term term;
    private Integer year;
    private TransportType transportType;
    private String studentFullName;
    private String transportName;
    private Double expectedFee;
    private Double totalPaid;
    private Double totalArrears;
    private LocalDateTime transactionTime;
    private String status;
}
