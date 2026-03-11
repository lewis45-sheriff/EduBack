package com.EduePoa.EP.Procurement.SupplierOnboarding.Requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierOnboardingRequestDTO {
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String idNumber;

    @NotBlank(message = "Business name is required")
    private String businessName;

    @NotBlank(message = "Business registration number is required")
    private String businessRegistrationNumber;

    @NotBlank(message = "Business type is required")
    private String businessType;

    @NotBlank(message = "Industry is required")
    private String industry;

    private String taxPinNumber;

    private LocalDate registrationDate;

    @NotBlank(message = "Business email is required")
    @Email(message = "Invalid email format")
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

    // Base64 encoded documents
    private String businessCertificate;
    private String kraPin;

    @NotNull(message = "User ID is required")
    private Long userId;
}
