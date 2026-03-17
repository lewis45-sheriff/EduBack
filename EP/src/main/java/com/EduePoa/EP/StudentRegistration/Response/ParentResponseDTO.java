package com.EduePoa.EP.StudentRegistration.Response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParentResponseDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String otherNames;
    private String fullName;
    private String phoneNumber;
    private String alternatePhoneNumber;
    private String email;
    private String nationalIdOrPassport;
    private String occupation;
    private String address;
    private boolean portalAccessEnabled;
}
