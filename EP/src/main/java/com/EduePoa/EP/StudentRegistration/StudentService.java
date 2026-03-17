package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.StudentRegistration.Request.CreateStudentRequestDTO;
import com.EduePoa.EP.StudentRegistration.Request.StudentRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface StudentService {
    CustomResponse<?>captureNewStudent(CreateStudentRequestDTO request);
    CustomResponse<?>getAllStudents();
    CustomResponse<?>getStudentById(Long id);
    CustomResponse<?>getFeeStructurePerStudent(Long studentId);
    CustomResponse<?>totalNumberStudents();
    CustomResponse<?>studentsPerGrade();
//    CustomResponse<?>bulkUploads(MultipartFile file);
    ResponseEntity<Resource> generateBulkUploadTemplate(String file);
    CustomResponse<?>getPerGrade(Long id);
    CustomResponse<?> getFeeStucturePerStudent(Long studentId);
    CustomResponse<?> getStudentGuardians(Long studentId);
    CustomResponse<?> getNemisStatus(Long studentId);

}
