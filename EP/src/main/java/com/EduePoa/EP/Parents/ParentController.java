package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Parents.Request.CreateParentRequestDTO;
import com.EduePoa.EP.Parents.Request.PortalAccessRequestDTO;
import com.EduePoa.EP.Parents.Request.UpdateParentRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/parents")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;

    @PostMapping("/create-parent")
    public ResponseEntity<?> createParentLong(@RequestBody CreateParentRequestDTO request) {
        var response = parentService.createParent(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping
    public ResponseEntity<?> createParent(@RequestBody CreateParentRequestDTO request) {
        var response = parentService.createParent(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/get-all-parents")
    public ResponseEntity<?> getAllParentsLong() {
        var response = parentService.getAllParents();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllParents() {
        var response = parentService.getAllParents();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getParentById(@PathVariable Long id) {
        var response = parentService.getParentById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateParent(@PathVariable Long id, @RequestBody UpdateParentRequestDTO request) {
        var response = parentService.updateParent(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteParent(@PathVariable Long id) {
        var response = parentService.deleteParent(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PatchMapping("/{id}/portal-access")
    public ResponseEntity<?> updatePortalAccess(@PathVariable Long id,
                                                @RequestBody PortalAccessRequestDTO request) {
        var response = parentService.updatePortalAccess(id, request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
