package com.EduePoa.EP.Transport.TransportTransactions.Requests;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.Data;

@Data
public class TransportTransactionRequestDTO {
    private Double amount;
    private String paymentMethod;
    private Term term;
    private Integer year;
    private Long transportId;
    private String transportType;
}