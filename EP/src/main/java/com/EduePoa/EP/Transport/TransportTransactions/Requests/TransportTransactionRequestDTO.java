package com.EduePoa.EP.Transport.TransportTransactions.Requests;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.Transport.TransportType;
import lombok.Data;

@Data
public class TransportTransactionRequestDTO {
    private Double amount;
    private String paymentMethod;
    private Term term;
    private Integer year;
    private Long vehicleId;
    private TransportType transportType;
}