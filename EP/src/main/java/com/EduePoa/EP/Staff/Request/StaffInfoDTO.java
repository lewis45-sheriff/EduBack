package com.EduePoa.EP.Staff.Request;

import com.EduePoa.EP.Staff.Enum.StaffType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StaffInfoDTO {
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
    /**
     * Requested portal access. Ignored for teachers, who are always granted a
     * login regardless of this value.
     */
    private boolean portalAccessEnabled = false;
    private boolean receiveSms = true;
    private boolean receiveEmail = false;
}
