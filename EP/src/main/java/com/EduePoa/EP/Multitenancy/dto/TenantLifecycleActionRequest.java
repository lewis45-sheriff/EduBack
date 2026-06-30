package com.EduePoa.EP.Multitenancy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantLifecycleActionRequest {

    @NotBlank(message = "Reason is required for lifecycle actions")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;
}
