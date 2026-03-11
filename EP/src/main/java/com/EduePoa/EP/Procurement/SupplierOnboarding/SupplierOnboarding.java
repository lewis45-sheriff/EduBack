package com.EduePoa.EP.Procurement.SupplierOnboarding;


import com.EduePoa.EP.Authentication.Enum.SupplierStatus;
import com.EduePoa.EP.Authentication.User.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "suppliers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class SupplierOnboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String businessName;

    @Column(nullable = false, unique = true)
    private String businessRegistrationNumber;

    @Column(nullable = false)
    private String businessType;

    @Column(nullable = false)
    private String industry;

    private String taxPinNumber;
    private LocalDate registrationDate;

    @Column(nullable = false)
    private String businessEmail;

    private String businessPhone;
    private String businessAddress;
    private String county;
    private String country;
    private String website;

    private String bankName;
    private String bankBranch;
    private String accountName;
    private String accountNumber;

    @Lob
    private String businessCertificate;

    @Lob
    private String kraPin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierStatus status = SupplierStatus.PENDING;

    @Column(length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    @JsonIgnore
    private User approvedBy;

    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    @JsonIgnore
    private User rejectedBy;

    private LocalDateTime rejectedAt;

    // Balance tracking for payments
    @Column(precision = 19, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    // Audit fields
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    @JsonIgnore
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @JsonIgnore
    private User updatedBy;

    private LocalDateTime updatedDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;
}
