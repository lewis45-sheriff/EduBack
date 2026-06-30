package com.EduePoa.EP.Multitenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantRegistrationRequest {

    @NotBlank(message = "Tenant identifier is required")
    @Size(min = 3, max = 63, message = "Tenant identifier must be between 3 and 63 characters")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Tenant identifier must be lowercase alphanumeric with hyphens (e.g., 'bureti-high')")
    private String tenantIdentifier;

    @NotBlank(message = "School name is required")
    @Size(max = 255, message = "School name must not exceed 255 characters")
    private String schoolName;

    @Size(max = 500, message = "Physical address must not exceed 500 characters")
    private String physicalAddress;

    @Size(max = 255, message = "Email domain must not exceed 255 characters")
    private String emailDomain;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    @Size(max = 500, message = "Logo URL must not exceed 500 characters")
    private String logoUrl;

    @Size(max = 50, message = "Subscription plan must not exceed 50 characters")
    private String subscriptionPlan;
}
