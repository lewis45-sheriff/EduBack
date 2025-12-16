package com.EduePoa.EP.StudentInvoices;
import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.FeeStructure.FeeStructure;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;

@Entity
@Table(
        name = "student_invoices",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"student_id", "term", "academic_year"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentInvoices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_structure_id", nullable = false)
    private FeeStructure feeStructure;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Term term;

    @Column(nullable = false)
    private Year academicYear;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal balance;


    @Column(nullable = false)
    private char status = 'P';
    // P = Pending, C = Cleared, O = Overdue

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    @Column(nullable = false)
    private char isDeleted = 'N';

    @PrePersist
    public void prePersist() {
        if (invoiceDate == null) {
            invoiceDate = LocalDate.now();
        }
        if (balance == null) {
            balance = totalAmount.subtract(amountPaid);
        }
    }
}
