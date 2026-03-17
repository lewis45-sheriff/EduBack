package com.EduePoa.EP.StudentRegistration.Request;

import lombok.Data;

@Data
public class ParentInfoDTO {
    private String firstName;
    private String lastName;
    private String otherNames;
    private String phoneNumber;
    private String alternatePhoneNumber;
    private String email;
    private String nationalIdOrPassport;
    private String occupation;
    private String address;
    private boolean portalAccessEnabled = false;
    private boolean receiveSms = true;
    private boolean receiveEmail = false;
}
