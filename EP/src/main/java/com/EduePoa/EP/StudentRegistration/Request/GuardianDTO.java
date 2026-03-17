package com.EduePoa.EP.StudentRegistration.Request;

import com.EduePoa.EP.StudentRegistration.GuardianRelationship;
import lombok.Data;

@Data
public class GuardianDTO {
    /** Set this to link an existing parent; omit to create a new one. */
    private Long parentId;

    private GuardianRelationship relationship;
    private boolean isPrimaryContact = false;
    private boolean isFeePayer = false;
    private Integer feeResponsibilityPercent = 0;
    private boolean pickupAuthorized = false;

    /**
     * Populated when creating a brand-new parent.
     * Must be null if parentId is provided.
     */
    private ParentInfoDTO parent;
}
