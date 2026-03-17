package com.EduePoa.EP.StudentRegistration.Response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentGuardianResponseDTO {
    private Long id;
    private Long studentId;
    private Long parentId;
    private String fullName;
    private String relationship;
    private String phoneNumber;
    private String email;
    private String nationalIdOrPassport;
    private boolean isPrimaryContact;
    private boolean isFeePayer;
    private boolean pickupAuthorized;
    private Integer feeResponsibilityPercent;
}
