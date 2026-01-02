package com.EduePoa.EP.StudentRegistration.Request;


import com.EduePoa.EP.StudentRegistration.Response.StudentResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkUploadResponseDTO {
    private int totalRecords;
    private int successCount;
    private int failureCount;
    private List<StudentResponseDTO> successfulStudents = new ArrayList<>();
    private List<BulkUploadError> errors = new ArrayList<>();
}

