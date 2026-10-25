package com.EduePoa.EP.OrgUnit.Controller;

import com.EduePoa.EP.OrgUnit.DTO.OrgUnitCreateRequest;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitReparentRequest;
import com.EduePoa.EP.OrgUnit.DTO.OrgUnitUpdateRequest;
import com.EduePoa.EP.OrgUnit.Service.OrgUnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Organizational-unit hierarchy management. Reads require {@code budget:read}; mutations require
 * {@code org_unit:manage}. All authorization is enforced server-side.
 */
@RestController
@RequestMapping("/api/v1/org-units")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasPermission(null, 'org_unit:manage')")
    public ResponseEntity<?> create(@Valid @RequestBody OrgUnitCreateRequest request) {
        var response = orgUnitService.create(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping({"/{id}", "/update/{id}"})
    @PreAuthorize("hasPermission(null, 'org_unit:manage')")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody OrgUnitUpdateRequest request) {
        var response = orgUnitService.update(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/{id}/reparent")
    @PreAuthorize("hasPermission(null, 'org_unit:manage')")
    public ResponseEntity<?> reparent(@PathVariable Long id, @Valid @RequestBody OrgUnitReparentRequest request) {
        var response = orgUnitService.reparent(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping({"/{id}", "/deactivate/{id}"})
    @PreAuthorize("hasPermission(null, 'org_unit:manage')")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        var response = orgUnitService.deactivate(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"/{id}", "/get-by-id/{id}"})
    @PreAuthorize("hasPermission(null, 'budget:read')")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        var response = orgUnitService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping({"", "/get-all"})
    @PreAuthorize("hasPermission(null, 'budget:read')")
    public ResponseEntity<?> getAll(@RequestParam(defaultValue = "false") boolean activeOnly) {
        var response = orgUnitService.getAll(activeOnly);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/tree")
    @PreAuthorize("hasPermission(null, 'budget:read')")
    public ResponseEntity<?> tree() {
        var response = orgUnitService.getTree();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
