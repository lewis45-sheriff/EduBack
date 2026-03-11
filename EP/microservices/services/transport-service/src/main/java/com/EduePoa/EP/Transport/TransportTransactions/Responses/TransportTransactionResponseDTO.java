package com.EduePoa.EP.Transport.TransportTransactions.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class TransportTransactionResponseDTO {
    private Long id;
    private Double amount;
    private String paymentMethod;
    private Term term;
    private Integer year;
    private String transportType;
    private String studentFullName;
    private String transportName;
    private Double expectedFee;
    private Double totalPaid;
    private Double totalArrears;
    private LocalDateTime transactionTime;
    private String status;
}
