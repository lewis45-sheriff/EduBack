package com.EduePoa.EP.Parents.Response;

import com.EduePoa.EP.StudentRegistration.GuardianRelationship;
import lombok.Builder;
import lombok.Data;

/**
 * A student linked to a parent (the parent's child), produced by
 * {@code GET /api/v1/parents/{parentId}/students}. Carries the student's identity plus the
 * parent-specific guardian attributes (relationship, primary contact, fee payer).
 */
@Data
@Builder
public class ParentStudentDTO {
    private Long studentId;
    private String admissionNumber;
    private String fullName;
    private String gradeName;
    private String streamName;
    private String gender;

    /** This parent's relationship to the student (e.g. MOTHER, FATHER, GUARDIAN). */
    private GuardianRelationship relationship;
    private boolean primaryContact;
    private boolean feePayer;
}
