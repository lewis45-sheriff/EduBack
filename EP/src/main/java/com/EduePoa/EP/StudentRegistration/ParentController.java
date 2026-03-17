package com.EduePoa.EP.StudentRegistration;

import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/parents/")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;

    @GetMapping("get-all-parents")
    public ResponseEntity<?> getAllParents() {
        var response = parentService.getAllParents();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}
