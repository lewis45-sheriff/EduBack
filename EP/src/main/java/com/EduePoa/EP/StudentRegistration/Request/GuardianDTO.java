package com.EduePoa.EP.StudentRegistration.Request;

import com.EduePoa.EP.Parents.Request.ParentInfoDTO;
import com.EduePoa.EP.StudentRegistration.GuardianRelationship;
import lombok.Data;

@Data
public class GuardianDTO {
    private Long parentId;
    private GuardianRelationship relationship;
    private boolean isPrimaryContact = false;
    private boolean isFeePayer = false;
    private Integer feeResponsibilityPercent = 0;
    private boolean pickupAuthorized = false;


    private ParentInfoDTO parent;
}
