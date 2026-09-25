package com.EduePoa.EP.StudentRegistration.OptionalFees;

import com.EduePoa.EP.Authentication.Enum.Term;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Request.StudentOptionalFeeAssignRequest;
import com.EduePoa.EP.StudentRegistration.OptionalFees.Request.StudentOptionalFeeBatchAssignRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;

/**
 * Endpoints for managing student optional-fee assignments.
 * <p>
 * Controller-level {@code @PreAuthorize} enforces the permission; the service
 * additionally enforces resource ownership / guardian relationship checks. Both
 * layers are required to prevent horizontal privilege escalation (IDOR).
 */
@RestController
@RequestMapping("api/v1/optional-fees/")
@RequiredArgsConstructor
public class StudentOptionalFeeController {

    private final StudentOptionalFeeService studentOptionalFeeService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'optional_fee:assign')")
    public ResponseEntity<?> assign(@RequestBody StudentOptionalFeeAssignRequest request) {
        var response = studentOptionalFeeService.assign(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("batch")
    @PreAuthorize("hasPermission(null, 'optional_fee:assign')")
    public ResponseEntity<?> assignBatch(@RequestBody StudentOptionalFeeBatchAssignRequest request) {
        var response = studentOptionalFeeService.assignBatch(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasPermission(null, 'optional_fee:remove')")
    public ResponseEntity<?> remove(@PathVariable Long id) {
        var response = studentOptionalFeeService.remove(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("student/{studentId}")
    @PreAuthorize("hasPermission(null, 'optional_fee:read')")
    public ResponseEntity<?> listForStudent(@PathVariable Long studentId,
                                            @RequestParam(required = false) Term term,
                                            @RequestParam(required = false) Year academicYear) {
        var response = studentOptionalFeeService.listForStudent(studentId, term, academicYear);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
