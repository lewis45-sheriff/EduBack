package com.EduePoa.EP.OrgUnit.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgUnitCreateRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "type is required")
    private String type;

    /** Parent unit id. Null is allowed ONLY when creating the single tenant root. */
    private Long parentId;

    /** Optional head user id for departmental scoping. */
    private Long headUserId;
}
