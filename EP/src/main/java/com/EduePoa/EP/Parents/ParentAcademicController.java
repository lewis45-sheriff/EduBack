package com.EduePoa.EP.Parents;

import com.EduePoa.EP.Reports.ReportCardRequest;
import com.EduePoa.EP.Reports.ReportsService;
import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.service.ReportCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/v1/parents/me")
@RequiredArgsConstructor
@Slf4j
public class ParentAcademicController {

    private final ParentService parentService;
    private final ReportCardService reportCardService;
    private final ReportsService reportsService;

    @GetMapping("/children/{studentId}/report-card")
    public ResponseEntity<?> getChildReportCard(@PathVariable Long studentId,
                                                @RequestParam Long termId,
                                                @RequestParam int year) {
        if (!parentService.currentParentOwnsStudent(studentId)) {
            return forbidden(studentId);
        }
        var res = reportCardService.getReportCard(studentId, termId, year);
        return ResponseEntity.status(res.getStatusCode()).body(res);
    }


    @PostMapping("/children/{studentId}/report-card/pdf")
    public ResponseEntity<?> downloadChildReportCardPdf(@PathVariable Long studentId,
                                                        @RequestBody ParentReportCardRequest body) {
        if (!parentService.currentParentOwnsStudent(studentId)) {
            return forbidden(studentId);
        }

        // Build the request for the shared Jasper generator. The studentId always comes from the
        // verified path variable, never from the request body, so a parent cannot target another
        // student by tampering with the payload.
        ReportCardRequest request = new ReportCardRequest();
        request.setStudentId(studentId);
        request.setTermId(body != null ? body.getTermId() : null);
        request.setYear(body != null ? body.getYear() : null);

        CustomResponse<?> res = reportsService.generateCbcReportCard(request);

        if (res.getStatusCode() == HttpStatus.OK.value()) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=report-card-" + studentId + "-" + body.getYear() + ".pdf");
            return ResponseEntity.status(res.getStatusCode())
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(res.getEntity());
        }

        return ResponseEntity.status(res.getStatusCode()).body(res);
    }

    private ResponseEntity<?> forbidden(Long studentId) {
        log.warn("Parent report-card access denied: current parent is not a guardian of student {}", studentId);
        CustomResponse<Object> res = new CustomResponse<>();
        res.setStatusCode(HttpStatus.FORBIDDEN.value());
        res.setMessage("You can only access your own children's records.");
        res.setEntity(null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(res);
    }

    /** Minimal body for the parent PDF download; studentId is taken from the verified path. */
    @lombok.Data
    public static class ParentReportCardRequest {
        private Integer termId;
        private Integer year;
    }
}
