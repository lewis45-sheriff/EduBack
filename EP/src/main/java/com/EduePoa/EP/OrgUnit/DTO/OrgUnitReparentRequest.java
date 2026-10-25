package com.EduePoa.EP.OrgUnit.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgUnitReparentRequest {

    /** New parent unit id. Must not be the unit itself or any of its descendants. */
    @NotNull(message = "newParentId is required")
    private Long newParentId;
}
