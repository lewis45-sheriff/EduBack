package com.EduePoa.EP.Staff.Request;

import com.EduePoa.EP.Staff.Enum.StaffType;
import lombok.Data;

import java.time.LocalDate;

/**
 * Partial-update payload. Only non-null fields are applied.
 */
@Data
public class UpdateStaffRequestDTO {
    private String firstName;
    private String lastName;
    private String otherNames;
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
    private Boolean portalAccessEnabled;
    private Boolean receiveSms;
    private Boolean receiveEmail;
}
