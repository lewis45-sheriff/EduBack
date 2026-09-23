package com.EduePoa.EP.Staff.Request;

import com.EduePoa.EP.Staff.Enum.StaffType;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.time.LocalDate;

/**
 * Accepts staff details either nested under a "staff" key or as flat top-level
 * fields, mirroring the parent onboarding request shape.
 */
@Data
public class CreateStaffRequestDTO {

    @JsonAlias("staff")
    private StaffInfoDTO staff;

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

    public StaffInfoDTO resolve() {
        if (staff != null) {
            return staff;
        }
        StaffInfoDTO dto = new StaffInfoDTO();
        dto.setFirstName(firstName);
        dto.setLastName(lastName);
        dto.setOtherNames(otherNames);
        dto.setEmployeeNumber(employeeNumber);
        dto.setPhoneNumber(phoneNumber);
        dto.setAlternatePhoneNumber(alternatePhoneNumber);
        dto.setEmail(email);
        dto.setNationalIdOrPassport(nationalIdOrPassport);
        dto.setGender(gender);
        dto.setAddress(address);
        dto.setStaffType(staffType);
        dto.setDepartment(department);
        dto.setDesignation(designation);
        dto.setDateOfEmployment(dateOfEmployment);
        dto.setPortalAccessEnabled(portalAccessEnabled != null && portalAccessEnabled);
        dto.setReceiveSms(receiveSms == null || receiveSms);
        dto.setReceiveEmail(receiveEmail != null && receiveEmail);
        return dto;
    }
}
