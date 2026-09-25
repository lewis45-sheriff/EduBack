package com.EduePoa.EP.StudentInvoices.Responses;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.FeeStructure.FeeMode;
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
    /**
     * The billing type the invoice was generated for (DAY / BOARDING), derived
     * from the student's boarding status. Weekly-boarding students are billed
     * using the BOARDING fee structure.
     */
    private FeeMode feeMode;
    private Term term;
    private Year academicYear;
    /** Mandatory fees from the fee structure for this term. */
    private BigDecimal mandatoryFeesAmount;
    /** Sum of optional fee assignments applied to this term. */
    private BigDecimal optionalFeesAmount;
    /** Arrears (positive) or credit (negative) carried forward from prior terms. */
    private BigDecimal carriedForwardAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private char status;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
}