package com.EduePoa.EP.Expenses;

import com.EduePoa.EP.Authentication.User.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expenses {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String expenseNumber;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String category;

    @Column(length = 50)
    private String paymentMethod;

    @Column(length = 500)
    private String description;

    @Column(length = 255)
    private String vendorName;

    @Column(length = 100)
    private String receiptNumber;

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 50)
    @Builder.Default
    private String status = "SUBMITTED";

    @Column(length = 1000)
    private String notes;

    // Audit fields
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
