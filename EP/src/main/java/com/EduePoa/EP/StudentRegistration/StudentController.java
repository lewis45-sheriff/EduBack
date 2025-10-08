package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.StudentRegistration.Request.StudentRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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


}
