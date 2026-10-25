package com.EduePoa.EP.OrgUnit.DTO;

import com.EduePoa.EP.OrgUnit.Entity.OrgUnitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Org-unit view. When returned from the tree endpoint, {@link #children} is populated to render
 * the hierarchy; for flat listings it is null.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgUnitResponse {
    private Long id;
    private String name;
    private String code;
    private String type;
    private Long parentId;
    private String path;
    private int depth;
    private OrgUnitStatus status;
    private Long headUserId;
    private String headUserName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Populated only in the tree view. */
    private List<OrgUnitResponse> children;
}
