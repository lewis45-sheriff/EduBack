package com.EduePoa.EP.Procurement.SupplierOnboarding.Responses;

import com.EduePoa.EP.Authentication.Enum.SupplierStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierOnboardingResponseDTO {

    private Long id;
    private String businessName;
    private String businessRegistrationNumber;
    private String businessType;
    private String industry;
    private String taxPinNumber;
    private LocalDate registrationDate;
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

    private SupplierStatus status;
    private String rejectionReason;
    private BigDecimal currentBalance;

    // Approval info
    private String approvedByName;
    private String approvedByEmail;
    private LocalDateTime approvedAt;

    private String rejectedByName;
    private String rejectedByEmail;
    private LocalDateTime rejectedAt;

    // Audit info
    private String createdByName;
    private LocalDateTime createdDate;
    private String updatedByName;
    private LocalDateTime updatedDate;

    // Document flags (not returning full base64)
    private boolean hasBusinessCertificate;
    private boolean hasKraPin;
}
