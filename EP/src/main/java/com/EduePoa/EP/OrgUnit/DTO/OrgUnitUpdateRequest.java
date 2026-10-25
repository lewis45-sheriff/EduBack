package com.EduePoa.EP.OrgUnit.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update a unit's own attributes (name, type, head). Re-parenting is a separate, validated
 * operation ({@link OrgUnitReparentRequest}); code is immutable (stable reporting reference).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgUnitUpdateRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "type is required")
    private String type;

    private Long headUserId;
}
