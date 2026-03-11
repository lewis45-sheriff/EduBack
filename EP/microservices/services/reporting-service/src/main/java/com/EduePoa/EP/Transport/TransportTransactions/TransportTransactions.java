package com.EduePoa.EP.Transport.TransportTransactions;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.Transport.Transport;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "transport_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportTransactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Term term;

    @Column(nullable = false)
    private Integer year;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "transport_id", nullable = false)
    private Transport transport;

    @Column(nullable = false)
    private String transportType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime transactionTime;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false)
    private Double expectedFee;

    @Column(nullable = false)
    private Double totalPaidBeforeThis;

    @Column(nullable = false)
    private Double totalPaidAfterThis;

    @Column(nullable = false)
    private Double arrearsAfterThis;

}
