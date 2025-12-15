package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.StudentRegistration.Request.StudentRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface StudentService {
    CustomResponse<?>captureNewStudent(StudentRequestDTO studentRequestDTO);
    CustomResponse<?>getAllStudents();
    CustomResponse<?>getStudentById(Long id);
    CustomResponse<?>getFeeStructurePerStudent(Long studentId);
    CustomResponse<?>totalNumberStudents();
}
