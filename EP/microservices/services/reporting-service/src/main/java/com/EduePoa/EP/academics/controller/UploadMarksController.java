package com.EduePoa.EP.academics.controller;


import com.EduePoa.EP.academics.dto.request.UploadMarksRequest;
import com.EduePoa.EP.academics.service.UploadMarksService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/v1/enter-marks")
@RequiredArgsConstructor
public class UploadMarksController {
    private final UploadMarksService uploadMarksService;

    @PostMapping("/create")
    public ResponseEntity<?> uploadMarks(@RequestBody List<UploadMarksRequest> requests) {
        var res = uploadMarksService.uploadMarks(requests);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }
}
