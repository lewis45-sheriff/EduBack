package com.EduePoa.EP.BankIntergration.BankResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
public class BankResponseDTO {
    private Long id;
    private String transType;
    private String transId;
    private String ftRef;
    private String transTime;
    private String transAmount;
    private String businessShortCode;
    private String billRefNumber;
    private String narrative;
    private String mobile;
    private String customerName;
    private String tranParticulars;
    private String accountNumber;
    private String statusCode;
    private String statusDescription;

    // Student details (instead of entire entity)
    private Long studentId;
    private String studentName;
    private String studentAdmissionNumber;

}
