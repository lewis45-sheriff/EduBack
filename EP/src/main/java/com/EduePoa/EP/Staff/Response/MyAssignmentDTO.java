package com.EduePoa.EP.Staff.Response;

import com.EduePoa.EP.Staff.Enum.StaffType;
import lombok.Builder;
import lombok.Data;

/**
 * Self-service view of the authenticated staff member's own identity and class
 * assignment. {@code assignment} is null when the staff member is not the class
 * teacher of anything (a valid, non-error state).
 */
@Data
@Builder
public class MyAssignmentDTO {
    private Long staffId;
    private Long userId;
    private String fullName;
    private String employeeNumber;
    private StaffType staffType;
    private ClassAssignmentDTO assignment;
}
