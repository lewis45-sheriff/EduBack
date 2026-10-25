package com.EduePoa.EP.Staff;

import com.EduePoa.EP.Staff.Request.CreateStaffRequestDTO;
import com.EduePoa.EP.Staff.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Staff.Request.UpdateStaffRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    @PreAuthorize("hasPermission(null, 'staff:create')")
    public ResponseEntity<?> createStaff(@RequestBody CreateStaffRequestDTO request) {
        var response = staffService.createStaff(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'staff:read')")
    public ResponseEntity<?> getAllStaff() {
        var response = staffService.getAllStaff();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Returns only staff who are teachers (linked system user with ROLE_TEACHER). Backs teacher
     * pickers such as "assign class teacher". Readable by staff:read or class:update holders so the
     * class-management UI can populate the dropdown without full staff-management rights.
     */
    @GetMapping("/teachers")
    @PreAuthorize("hasPermission(null, 'staff:read') or hasPermission(null, 'class:update') or hasPermission(null, 'class:read')")
    public ResponseEntity<?> getTeachers() {
        var response = staffService.getTeachers();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Self-service: returns the authenticated caller's own staff identity and class
     * assignment. Authorized by identity only — deliberately NOT gated by staff:read,
     * so teachers do not need admin staff-management permissions to reach it.
     */
    @GetMapping("/my-assignment")
    public ResponseEntity<?> getMyAssignment() {
        var response = staffService.getMyAssignment();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:read')")
    public ResponseEntity<?> getStaffById(@PathVariable Long id) {
        var response = staffService.getStaffById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    public ResponseEntity<?> updateStaff(@PathVariable Long id, @RequestBody UpdateStaffRequestDTO request) {
        var response = staffService.updateStaff(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'staff:delete')")
    public ResponseEntity<?> deleteStaff(@PathVariable Long id) {
        var response = staffService.deleteStaff(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/{id}/portal-access")
    @PreAuthorize("hasPermission(null, 'staff:update')")
    public ResponseEntity<?> updatePortalAccess(@PathVariable Long id,
                                                @RequestBody PortalAccessRequestDTO request) {
        var response = staffService.updatePortalAccess(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Idempotently syncs this tenant's ROLE_TEACHER permission set with the current Teacher Portal
     * features (adds any missing permissions such as curriculum:read / report:generate). Requires
     * role management permission. Safe to call repeatedly.
     */
    @PostMapping("/roles/teacher/sync-permissions")
    @PreAuthorize("hasPermission(null, 'role:update')")
    public ResponseEntity<?> syncTeacherRolePermissions() {
        var response = staffService.syncTeacherRolePermissions();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
