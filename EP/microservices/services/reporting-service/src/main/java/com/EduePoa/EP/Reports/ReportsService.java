package com.EduePoa.EP.Reports;

import com.EduePoa.EP.Utils.CustomResponse;
import com.google.gson.Gson;
import net.sf.jasperreports.engine.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportsService {

    @Value("${spring.datasource.url}")
    String db;

    @Value("${spring.datasource.username}")
    String username;

    @Value("${spring.datasource.password}")
    String password;

    @Value("${report_path}")
    String path;

    private Map<String, Object> setParameters(ReportModel reportRequestObject) {
        Map<String, Object> parameters = new HashMap<>();

        parameters.put("file_name", reportRequestObject.fileName);
        parameters.put("report_path", path);
        parameters.put("Logo", resolveLogoPath(null));

        return parameters;
    }

    public CustomResponse<?> generateReportCard(ReportCardRequest request) {
        CustomResponse<Object> res = new CustomResponse<>();

        if (request == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("Request body is required");
            return res;
        }

        if (request.getStudentId() == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("studentId is required");
            return res;
        }
        if (request.getGradeId() == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("gradeId is required");
            return res;
        }
        if (request.getTermId() == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("termId is required");
            return res;
        }
        if (request.getYear() == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("year is required");
            return res;
        }

        String termCode = mapTermIdToCode(request.getTermId());
        if (termCode == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("Invalid termId. Use 1, 2, or 3");
            return res;
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("report_path", path);
        parameters.put("file_name", FileTypeEnums.TERM_PERFORMANCE.getReportTypeString());
        parameters.put("studentID", request.getStudentId());
        parameters.put("gradeId", request.getGradeId());
        parameters.put("termID", termCode);
        parameters.put("year", request.getYear().longValue());
        parameters.put("Logo", resolveLogoPath(request.getLogoPath()));
        if (StringUtils.hasText(request.getSchoolName())) {
            parameters.put("SchoolName", request.getSchoolName());
        }
        if (StringUtils.hasText(request.getSchoolMotto())) {
            parameters.put("SchoolMotto", request.getSchoolMotto());
        }
        if (StringUtils.hasText(request.getSchoolContact())) {
            parameters.put("SchoolContact", request.getSchoolContact());
        }

        String reportPath = path + FileTypeEnums.TERM_PERFORMANCE.getReportTypeString();
        File reportFile = new File(reportPath);
        if (!reportFile.exists()) {
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.setMessage("Report template not found at " + reportPath);
            return res;
        }

        try (InputStream reportStream = new FileInputStream(reportFile);
             Connection connection = DriverManager.getConnection(db, username, password)) {
            JasperReport compiledReport = JasperCompileManager.compileReport(reportStream);
            JasperPrint report = JasperFillManager.fillReport(compiledReport, parameters, connection);
            if (report.getPages() == null || report.getPages().isEmpty()) {
                res.setStatusCode(HttpStatus.NOT_FOUND.value());
                res.setMessage(String.format(
                        "No report data found for studentID=%s, gradeId=%s, termID=%s, year=%s",
                        request.getStudentId(),
                        request.getGradeId(),
                        termCode,
                        request.getYear()));
                return res;
            }
            byte[] data = JasperExportManager.exportReportToPdf(report);

            res.setEntity(data);
            res.setStatusCode(HttpStatus.OK.value());
            res.setMessage("Report card generated successfully");
            return res;

        } catch (JRException e) {
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.setMessage("Report generation error: " + e.getMessage());
            return res;
        } catch (Exception e) {
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.setMessage("Unexpected error: " + e.getMessage());
            return res;
        }
    }

    public CustomResponse<?> dynamicReportCreate(
            Long studentID,
            ReportModel model,
            String term, // This is passed from controller
            Long year,
            Long parentId,
            Long gradeId
    ) {
        CustomResponse<Object> res = new CustomResponse<>();
        Gson gson = new Gson();
        String reportRequest = gson.toJson(model);
        ReportModel reportRequestObject = new Gson().fromJson(reportRequest, ReportModel.class);

        try {
            String reportPath = path + reportRequestObject.fileName;
            System.out.println("Loading report from path: " + reportPath);
            File reportFile = new File(reportPath);

            if (!reportFile.exists()) {
                throw new FileNotFoundException("Report file not found at " + reportPath);
            }

            Map<String, Object> parameters = setParameters(reportRequestObject);
            boolean isTermPerformance = FileTypeEnums.TERM_PERFORMANCE.getReportTypeString()
                    .equalsIgnoreCase(reportRequestObject.fileName)
                    || FileTypeEnums.REPORT_CARD.getReportTypeString().equalsIgnoreCase(reportRequestObject.fileName);

            // **CRITICAL FIX: The JRXML expects studentID as Long, not String**
            // Check your JRXML: <parameter name="studentID" class="java.lang.Long"/>

            // Add query parameters with correct types as per JRXML
            if (studentID != null) {
                // JRXML expects Long for studentID
                parameters.put("studentID", studentID);  // Pass as Long directly
            } else {
                // Handle case where studentID is null but required
                res.setMessage("Student ID is required");
                res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return res;
            }

            // Add other parameters if they exist in JRXML
            // Remove gradeId, parentId if not used in JRXML
            // parameters.put("gradeId", gradeId);  // Comment out if not in JRXML
            // parameters.put("parentId", parentId); // Comment out if not in JRXML

            // **FIX: year parameter should be Integer as per JRXML**
            if (year != null) {
                parameters.put("year", year);
            } else {
                // Handle case where year is null but required
                res.setMessage("Year is required");
                res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return res;
            }

            // **FIX: term parameter should be String as per JRXML**
            if (term != null && !term.isEmpty()) {
                parameters.put("termID", isTermPerformance ? normalizeTermValue(term) : term);
            } else {
                // Handle case where term is null but required
                res.setMessage("Term is required");
                res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return res;
            }

            if (isTermPerformance) {
                if (gradeId == null) {
                    res.setMessage("Grade ID is required");
                    res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                    return res;
                }
                parameters.put("gradeId", gradeId);
            }

            // Debug: Print parameters being passed
            System.out.println("=== REPORT PARAMETERS ===");
            parameters.forEach((key, value) ->
                    System.out.println("  " + key + ": " + value + " (Type: " +
                            (value != null ? value.getClass().getSimpleName() : "null") + ")")
            );
            System.out.println("=========================");

            // Additional debug: Test the SQL with parameters
            System.out.println("Testing SQL with parameters:");
            System.out.println("  studentID: " + parameters.get("studentID") +
                    " Type: " + (parameters.get("studentID") != null ?
                    parameters.get("studentID").getClass().getSimpleName() : "null"));
            System.out.println("  termID: " + parameters.get("termID") +
                    " Type: " + (parameters.get("termID") != null ?
                    parameters.get("termID").getClass().getSimpleName() : "null"));
            System.out.println("  year: " + parameters.get("year") +
                    " Type: " + (parameters.get("year") != null ?
                    parameters.get("year").getClass().getSimpleName() : "null"));

            try (InputStream reportStream = new FileInputStream(reportFile);
                 Connection connection = DriverManager.getConnection(db, username, password)) {
                JasperReport compiledReport = JasperCompileManager.compileReport(reportStream);
                JasperPrint report = JasperFillManager.fillReport(compiledReport, parameters, connection);

                if (report.getPages() == null || report.getPages().isEmpty()) {
                    res.setMessage(String.format(
                            "No report data found for studentID=%s, gradeId=%s, termID=%s, year=%s",
                            studentID,
                            gradeId,
                            parameters.get("termID"),
                            year));
                    res.setStatusCode(HttpStatus.NOT_FOUND.value());
                    return res;
                }

                byte[] data = JasperExportManager.exportReportToPdf(report);
                res.setEntity(data);
                res.setStatusCode(HttpStatus.OK.value());
                res.setMessage("Successfully generated report");
            }

        } catch (FileNotFoundException e) {
            System.err.println("FileNotFoundException: " + e.getMessage());
            res.setMessage("Report template not found: " + e.getMessage());
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        } catch (JRException e) {
            System.err.println("=== JRException Details ===");
            System.err.println("Message: " + e.getMessage());
            System.err.println("Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "None"));
            e.printStackTrace();

            // Try to get more specific error
            if (e.getMessage().contains("termID")) {
                res.setMessage("Report parameter error: The report expects 'termID' parameter. Check your report design.");
            } else if (e.getMessage().contains("studentID")) {
                res.setMessage("Report parameter error: The report expects 'studentID' parameter as Long type.");
            } else if (e.getMessage().contains("year")) {
                res.setMessage("Report parameter error: The report expects 'year' parameter as Integer type.");
            } else {
                res.setMessage("Report generation error: " + e.getMessage());
            }
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace();
            res.setMessage("Unexpected error: " + e.getMessage());
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        return res;
    }

    private String mapTermIdToCode(Integer termId) {
        return switch (termId) {
            case 1 -> "TERM_1";
            case 2 -> "TERM_2";
            case 3 -> "TERM_3";
            default -> null;
        };
    }

    private String normalizeTermValue(String term) {
        if (!StringUtils.hasText(term)) {
            return term;
        }
        String trimmed = term.trim();
        return switch (trimmed) {
            case "1" -> "TERM_1";
            case "2" -> "TERM_2";
            case "3" -> "TERM_3";
            default -> trimmed;
        };
    }

    private String resolveLogoPath(String logoPath) {
        if (StringUtils.hasText(logoPath)) {
            return logoPath;
        }
        return path + "effort-schools-logo.jpg";
    }
}
