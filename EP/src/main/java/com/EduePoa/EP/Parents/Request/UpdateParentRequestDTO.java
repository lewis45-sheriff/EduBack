package com.EduePoa.EP.Parents.Request;

import lombok.Data;

@Data
public class UpdateParentRequestDTO {
    private String firstName;
    private String lastName;
    private String otherNames;
    private String phoneNumber;
    private String alternatePhoneNumber;
    private String email;
    private String nationalIdOrPassport;
    private String occupation;
    private String address;
    private Boolean portalAccessEnabled;
    private Boolean receiveSms;
    private Boolean receiveEmail;
}
