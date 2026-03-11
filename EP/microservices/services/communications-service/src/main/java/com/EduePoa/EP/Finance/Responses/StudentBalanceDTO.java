package com.EduePoa.EP.Finance.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.*;

import java.math.BigDecimal;
import java.time.Year;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentBalanceDTO {

    private Long studentId;
    private BigDecimal totalFeeAmount;
    private BigDecimal paidAmount;
    private String gradeName;

    private BigDecimal balance;
    private Term term;
    private Year year;
    private String balanceStatus; // "OUTSTANDING", "OVERPAID", "CLEARED"

    // Optional: Add student name if you need it from Student entity
    private String studentName;
    private String studentEmail;
}