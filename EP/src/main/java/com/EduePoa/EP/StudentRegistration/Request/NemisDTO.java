package com.EduePoa.EP.StudentRegistration.Request;

import lombok.Data;

@Data
public class NemisDTO {
    private String upi;
    private String registrationIdentifierType;
    private String registrationIdentifierValue;
    private boolean queueSync = false;
}
