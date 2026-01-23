package com.EduePoa.EP.Transport.TransportTransactions.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.Data;

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
}
