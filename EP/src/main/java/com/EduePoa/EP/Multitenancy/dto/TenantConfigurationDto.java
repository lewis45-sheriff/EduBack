package com.EduePoa.EP.Multitenancy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfigurationDto {

    @NotBlank(message = "Configuration key is required")
    private String configKey;

    private String configValue;

    private Boolean isSensitive;
}
