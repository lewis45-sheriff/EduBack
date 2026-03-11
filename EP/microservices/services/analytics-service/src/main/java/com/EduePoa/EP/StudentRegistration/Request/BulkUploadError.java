package com.EduePoa.EP.StudentRegistration.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadError {
    private int rowNumber;
    private String admissionNumber;
    private String errorMessage;
}
