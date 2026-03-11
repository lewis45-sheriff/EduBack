package com.EduePoa.EP.StudentInvoices.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentInvoiceResponseDTO {

    private Long invoiceId;
    private String studentName;
    private String admissionNumber;
    private String grade;
    private Term term;
    private Year academicYear;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private char status;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
}