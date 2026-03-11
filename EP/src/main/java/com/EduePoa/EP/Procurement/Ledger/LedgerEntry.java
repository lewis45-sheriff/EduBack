package com.EduePoa.EP.Procurement.Ledger;


import com.EduePoa.EP.Authentication.Enum.TransactionType;
import com.EduePoa.EP.Authentication.User.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ledger_entries", indexes = {@Index(name = "idx_ledger_date", columnList = "transactionDate"), @Index(name = "idx_ledger_ref", columnList = "referenceType, referenceId")})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(precision = 19, scale = 2)
    private BigDecimal runningBalance;
    @Column(length = 500)
    private String description;
    @Column(nullable = false, length = 50)
    private String referenceType;
    @Column(nullable = false)
    private Long referenceId;
    @Column(length = 100)
    private String referenceNumber;
    @Column(nullable = false)
    private LocalDate transactionDate;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
