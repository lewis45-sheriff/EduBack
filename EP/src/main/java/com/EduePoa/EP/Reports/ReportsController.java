package com.EduePoa.EP.Reports;

import com.EduePoa.EP.Utils.CustomResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("api/v1/reports-j")
@Slf4j
public class ReportsController {

    @Autowired
    private ReportsService reportsService;

    @GetMapping("/load/{type}")
    public ResponseEntity<?> loadPdf(
            @PathVariable(value = "type") String reportType,
            @RequestParam(required = false) String studentID,
            @RequestParam(required = false) String gradeId,
            @RequestParam(required = false) String parentId,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String term) {

        CustomResponse<?> res = new CustomResponse<>();
        log.info("Generating report with inputs type {}, studentid {}, year {}, term {}, gradeId {}, parentId {}",
                reportType, studentID, year, term, gradeId, parentId);

        try {
            // Validate report type using FileTypeEnums
            FileTypeEnums reportTypeEnum = FileTypeEnums.fromFileName(reportType);
            if (reportTypeEnum == null) {
                res.setMessage("NO SUCH REPORT EXISTS");
                res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                log.error("No report type found {}", reportType);
                return ResponseEntity.status(res.getStatusCode()).body(res);
            }

            // Validate required parameters based on report type
            if (reportTypeEnum == FileTypeEnums.FEE_STATEMENT) {
                if (studentID == null || studentID.isEmpty()) {
                    res.setMessage("Student ID is required for fee statement");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return ResponseEntity.status(res.getStatusCode()).body(res);
                }
                if (term == null || term.isEmpty()) {
                    res.setMessage("Term is required for fee statement");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return ResponseEntity.status(res.getStatusCode()).body(res);
                }
                if (year == null || year.isEmpty()) {
                    res.setMessage("Year is required for fee statement");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return ResponseEntity.status(res.getStatusCode()).body(res);
                }
            }

            // Prepare report model
            ReportModel reportModel = new ReportModel();
            reportModel.setFileName(reportTypeEnum.getReportTypeString());

            // Set parameters if provided
            if (studentID != null && !studentID.isEmpty()) {
                try {
                    Long studentId = Long.valueOf(studentID);
                    reportModel.setStudentID(BigDecimal.valueOf(studentId));
                } catch (NumberFormatException e) {
                    res.setMessage("Invalid student ID format");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return ResponseEntity.status(res.getStatusCode()).body(res);
                }
            }

            if (gradeId != null && !gradeId.isEmpty()) {
                try {
                    Long gradeidLong = Long.valueOf(gradeId);
                    reportModel.setGradeId(BigDecimal.valueOf(gradeidLong));
                } catch (NumberFormatException e) {
                    res.setMessage("Invalid grade ID format");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return ResponseEntity.status(res.getStatusCode()).body(res);
                }
            }

            if (year != null && !year.isEmpty()) {
                try {
                    Long yearLong = Long.valueOf(year);
                    reportModel.setYear(BigDecimal.valueOf(yearLong));
                } catch (NumberFormatException e) {
                    res.setMessage("Invalid year format");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return ResponseEntity.status(res.getStatusCode()).body(res);
                }
            }

            if (parentId != null && !parentId.isEmpty()) {
                try {
                    Long parentIdLong = Long.valueOf(parentId);
                    reportModel.setParentId(BigDecimal.valueOf(parentIdLong));
                } catch (NumberFormatException e) {
                    res.setMessage("Invalid parent ID format");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return ResponseEntity.status(res.getStatusCode()).body(res);
                }
            }

            //  Keep term as string, the service will pass it as termID**
            reportModel.setTerm(term);

            // Generate report
            res = reportsService.dynamicReportCreate(
                    studentID != null && !studentID.isEmpty() ? Long.valueOf(studentID) : null,
                    reportModel,
                    term, // Pass term as string
                    year != null && !year.isEmpty() ? Long.valueOf(year) : null,
                    parentId != null && !parentId.isEmpty() ? Long.valueOf(parentId) : null,
                    gradeId != null && !gradeId.isEmpty() ? Long.valueOf(gradeId) : null
            );

            if (res.getStatusCode() == HttpStatus.OK.value()) {
                log.info("Report generated successfully: {}", reportType);
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + reportType + "-report.pdf");

                return ResponseEntity.status(res.getStatusCode())
                        .headers(headers)
                        .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                        .body(res.getEntity());
            } else {
                log.error("Report generation failed: {}", res.getMessage());
                return ResponseEntity.status(res.getStatusCode()).body(res);
            }

        } catch (Exception e) {
            log.error("Error generating report: {}", e.getMessage(), e);
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.setMessage("An error occurred: " + e.getMessage());
            return ResponseEntity.status(res.getStatusCode()).body(res);
        }
    }
}