package com.EduePoa.EP.Staff.Response;

import com.EduePoa.EP.Staff.Enum.StaffType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class StaffResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String otherNames;
    private String fullName;
    private String employeeNumber;
    private String phoneNumber;
    private String alternatePhoneNumber;
    private String email;
    private String nationalIdOrPassport;
    private String gender;
    private String address;
    private StaffType staffType;
    private String department;
    private String designation;
    private LocalDate dateOfEmployment;
    private boolean portalAccessEnabled;
    /** System user ID — populated when the staff member has a login account. */
    private Long userId;
}
