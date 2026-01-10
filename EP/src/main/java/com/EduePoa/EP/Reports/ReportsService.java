package com.EduePoa.EP.Reports;

import com.EduePoa.EP.Utils.CustomResponse;
import com.google.gson.Gson;
import net.sf.jasperreports.engine.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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

        return parameters;
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

        Connection connection = null;
        try {
            String reportPath = path + reportRequestObject.fileName;
            System.out.println("Loading report from path: " + reportPath);
            File reportFile = new File(reportPath);

            if (!reportFile.exists()) {
                throw new FileNotFoundException("Report file not found at " + reportPath);
            }

            InputStream reportStream = new FileInputStream(reportFile);
            JasperReport compiledReport = JasperCompileManager.compileReport(reportStream);

            Map<String, Object> parameters = setParameters(reportRequestObject);

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
                parameters.put("year", year);  // Pass as Long, JRXML expects Integer
            } else {
                // Handle case where year is null but required
                res.setMessage("Year is required");
                res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return res;
            }

            // **FIX: term parameter should be String as per JRXML**
            if (term != null && !term.isEmpty()) {
                parameters.put("termID", term);  // Pass as String
            } else {
                // Handle case where term is null but required
                res.setMessage("Term is required");
                res.setStatusCode(HttpStatus.BAD_REQUEST.value());
                return res;
            }

            // Debug: Print parameters being passed
            System.out.println("=== REPORT PARAMETERS ===");
            parameters.forEach((key, value) ->
                    System.out.println("  " + key + ": " + value + " (Type: " +
                            (value != null ? value.getClass().getSimpleName() : "null") + ")")
            );
            System.out.println("=========================");

            connection = DriverManager.getConnection(db, username, password);

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

            JasperPrint report = JasperFillManager.fillReport(compiledReport, parameters, connection);

            byte[] data = JasperExportManager.exportReportToPdf(report);
            res.setEntity(data);
            res.setStatusCode(HttpStatus.OK.value());
            res.setMessage("Successfully generated report");

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
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    System.err.println("Error closing database connection: " + e.getMessage());
                }
            }
        }

        return res;
    }
}