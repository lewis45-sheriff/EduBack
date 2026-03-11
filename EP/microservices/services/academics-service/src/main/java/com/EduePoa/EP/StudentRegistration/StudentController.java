package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.StudentRegistration.Request.StudentRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;


@RestController
@RequestMapping("api/v1/students/")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService studentService;

    @PostMapping("create")
    ResponseEntity<?>captureNewStudent(@RequestBody StudentRequestDTO studentRequestDTO){
        var response = studentService.captureNewStudent(studentRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-all-students")
    ResponseEntity<?>getAllStudents(){
        var response = studentService.getAllStudents();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-student-by-id/{id}")
    ResponseEntity<?>getStudentById(@PathVariable Long id){
        var response = studentService.getStudentById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("get-fee-structure-per-student/{studentId}")
    ResponseEntity<?> getFeeStructurePerStudent(@PathVariable Long studentId){
        var response = studentService.getFeeStructurePerStudent(studentId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("total-number-students")
    ResponseEntity<?>totalNumberStudents(){
        var response = studentService.totalNumberStudents();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("students-per-grade")
    ResponseEntity<?>studentsPerGrade(){
        var response = studentService.studentsPerGrade();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> bulkUploadStudents(
            @RequestParam("file") MultipartFile file) {
        var response = studentService.bulkUploads(file);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/bulk-upload/template")
    public ResponseEntity<Resource> downloadBulkUploadTemplate(
            @RequestParam(defaultValue = "excel") String type) {
        return studentService.generateBulkUploadTemplate(type);
    }
    @GetMapping("get-per-grade/{id}")
    public ResponseEntity<?> getPerGrade(@PathVariable Long id) {
        var response = studentService.getPerGrade(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }




}
