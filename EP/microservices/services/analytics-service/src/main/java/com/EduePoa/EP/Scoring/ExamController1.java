package com.EduePoa.EP.Scoring;

import com.EduePoa.EP.Scoring.Requests.CreateExamTypeGradingRequest;
import com.EduePoa.EP.Scoring.Requests.CreateExamTypeRequest;
import com.EduePoa.EP.Scoring.Requests.StudentBulkRequest;
import com.EduePoa.EP.Scoring.Requests.UpdateExamTypeRequest;
import com.EduePoa.EP.Scoring.Response.SubjectStudentResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/exams/")
public class ExamController1 {

    @Autowired
    private ExamService examService;

    @PostMapping("types/create")
//    @PreAuthorize("hasPermission(null, 'exam:create')")
    public ResponseEntity<?> createExamType(@Valid @RequestBody CreateExamTypeRequest request) {
        var response = examService.createExamType(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("types/get-all")
//    @PreAuthorize("hasPermission(null, 'exam:read')")
    public ResponseEntity<?> getAllExamTypes() {
        var response = examService.getAllExamTypes();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("types/edit")
//    @PreAuthorize("hasPermission(null, 'exam:update')")
    public ResponseEntity<?> updateExamType(@Valid @RequestBody UpdateExamTypeRequest request) {
        var response = examService.updateExamType(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("types/delete/{id}")
//    @PreAuthorize("hasPermission(null, 'exam:delete')")
    public ResponseEntity<?> deleteExamType(@PathVariable Long id) {
        var response = examService.deleteExamType(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("grading-criteria/create")
//    @PreAuthorize("hasPermission(null, 'exam:create')")
    public ResponseEntity<?> createExamTypeGradingCriteria(
            @Valid @RequestBody List<CreateExamTypeGradingRequest> request) {
        var response = examService.createExamTypeGradingCriteria(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("grading-criteria/view")
//    @PreAuthorize("hasPermission(null, 'exam:read')")
    public ResponseEntity<?> getExamTypeGradingCriteria() {
        var response = examService.getExamTypeGradingCriteria();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("scores/insert")
//    @PreAuthorize("hasPermission(null, 'exam:create')")
    public ResponseEntity<?> insertStudentScore(@RequestBody StudentBulkRequest request) {
        var response = examService.insertStudentScores(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/scores/view/{gradeId}/{subjectId}/{termId}")
//    @PreAuthorize("hasPermission(null, 'exam:read')")
    public ResponseEntity<?> getStudentScorePerTermGradeSubject(@PathVariable Long gradeId, @PathVariable Long termId,
            @PathVariable Long subjectId) {
        var response = examService.getStudentScorePerTermGradeSubject(gradeId, termId, subjectId);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

//    @GetMapping("/all-students/{year}/{gradeId}/{subjectId}")
////    @PreAuthorize("hasPermission(null, 'exam:read')")
//    public ResponseEntity<?> getSubjectsByGrade(
//            @PathVariable("year") int year,
//            @PathVariable("gradeId") Long gradeId,
//            @PathVariable(value = "subjectId", required = false) Long subjectId) {
//
//        var response = subjectService.getSubjectStudentsPerGrade(year, gradeId, null, subjectId);
//        return ResponseEntity.status(response.getStatusCode()).body(response);
//    }

    @GetMapping("/subject-marks")
//    @PreAuthorize("hasPermission(null, 'exam:read')")
    public ResponseEntity<?> getSubjectsByTerm() {
        var response = examService.getSubjectsByTerm();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/update/subject/{Id}")
//    @PreAuthorize("hasPermission(null, 'exam:update')")
    public ResponseEntity<?> getSubjectDetails(@PathVariable Long Id,
            @RequestBody SubjectStudentResponse subjectStudentResponse) {
        var response = examService.updateSubjectDetails(Id, subjectStudentResponse);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/get-subject-marks/{Id}")
//    @PreAuthorize("hasPermission(null, 'exam:read')")
    public ResponseEntity<?> getSubjectMarks(@PathVariable Long Id) {
        var response = examService.getSubjectMarks(Id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

}
