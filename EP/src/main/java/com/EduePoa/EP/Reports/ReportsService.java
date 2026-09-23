package com.EduePoa.EP.Reports;

import com.EduePoa.EP.Multitenancy.config.TenantContext;
import com.EduePoa.EP.Multitenancy.service.TenantConfigurationService;
import com.EduePoa.EP.Utils.CustomResponse;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import org.springframework.beans.factory.annotation.Autowired;
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
@Slf4j
public class ReportsService {

    @Value("${spring.datasource.url}")
    String db;

    @Value("${spring.datasource.username}")
    String username;

    @Value("${spring.datasource.password}")
    String password;

    @Value("${report_path}")
    String path;

    @Autowired
    private TenantConfigurationService tenantConfigurationService;

    // Config keys for tenant branding
    private static final String CONFIG_SCHOOL_NAME = "reports.school_name";
    private static final String CONFIG_LOGO_URL = "reports.logo_url";
    private static final String CONFIG_ADDRESS = "reports.address";

    /**
     * Retrieves tenant branding parameters (school name, logo URL, address) from
     * TenantConfigurationService for the current tenant context.
     * <p>
     * Returns a map with keys: "SchoolName", "Logo", "SchoolAddress".
     * Values are null if the tenant has not configured them.
     * Falls back gracefully if TenantContext is not set (returns empty map).
     *
     * @return map of branding parameters for the current tenant
     */
    public Map<String, String> getTenantBranding() {
        Map<String, String> branding = new HashMap<>();

        if (!TenantContext.isSet()) {
            log.debug("TenantContext not set; skipping tenant branding injection");
            return branding;
        }

        String tenantId = TenantContext.getCurrentTenant();

        String schoolName = tenantConfigurationService.getConfigOrDefault(tenantId, CONFIG_SCHOOL_NAME, null);
        String logoUrl = tenantConfigurationService.getConfigOrDefault(tenantId, CONFIG_LOGO_URL, null);
        String address = tenantConfigurationService.getConfigOrDefault(tenantId, CONFIG_ADDRESS, null);

        if (schoolName != null) {
            branding.put("SchoolName", schoolName);
        }
        if (logoUrl != null) {
            branding.put("Logo", logoUrl);
        }
        if (address != null) {
            branding.put("SchoolAddress", address);
        }

        log.debug("Resolved tenant branding for tenant '{}': schoolName={}, logo={}, address={}",
                tenantId, schoolName, logoUrl != null ? "[set]" : "[not set]", address);

        return branding;
    }

    private Map<String, Object> setParameters(ReportModel reportRequestObject) {
        Map<String, Object> parameters = new HashMap<>();

        parameters.put("file_name", reportRequestObject.fileName);
        parameters.put("report_path", path);
        parameters.put("Logo", safeLogo(resolveLogoPath(null)));

        // Inject tenant branding from TenantConfigurationService
        Map<String, String> branding = getTenantBranding();
        if (branding.containsKey("SchoolName")) {
            parameters.put("SchoolName", branding.get("SchoolName"));
        }
        if (branding.containsKey("Logo")) {
            // Tenant-configured logo overrides the default
            parameters.put("Logo", safeLogo(branding.get("Logo")));
        }
        if (branding.containsKey("SchoolAddress")) {
            parameters.put("SchoolAddress", branding.get("SchoolAddress"));
        }

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
        parameters.put("Logo", safeLogo(resolveLogoPath(request.getLogoPath())));

        // Inject tenant branding as defaults
        Map<String, String> branding = getTenantBranding();
        if (branding.containsKey("SchoolName")) {
            parameters.put("SchoolName", branding.get("SchoolName"));
        }
        if (branding.containsKey("Logo") && !StringUtils.hasText(request.getLogoPath())) {
            // Only override logo if the request didn't specify one
            parameters.put("Logo", safeLogo(branding.get("Logo")));
        }
        if (branding.containsKey("SchoolAddress")) {
            parameters.put("SchoolAddress", branding.get("SchoolAddress"));
        }

        // Request-provided values override tenant branding (backward compatibility)
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

    /**
     * Generates the CBC learner report card PDF from the new curriculum/assessment tables
     * (cbc_grade_result, cbc_performance_level, cbc_competency_evidence, cbc_value).
     * <p>
     * Jasper fills the report through a raw JDBC connection, which bypasses the Hibernate
     * tenant filter, so the current tenant is passed explicitly as the {@code tenantId}
     * parameter and every query in the template is scoped by {@code tenant_id = $P{tenantId}}.
     */
    public CustomResponse<?> generateCbcReportCard(ReportCardRequest request) {
        CustomResponse<Object> res = new CustomResponse<>();

        if (request == null || request.getStudentId() == null
                || request.getTermId() == null || request.getYear() == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("studentId, termId and year are required");
            return res;
        }

        String termCode = mapTermIdToCode(request.getTermId());
        if (termCode == null) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("Invalid termId. Use 1, 2, or 3");
            return res;
        }

        if (!TenantContext.isSet()) {
            res.setStatusCode(HttpStatus.BAD_REQUEST.value());
            res.setMessage("Tenant context is required to generate a report card");
            return res;
        }
        String tenantId = TenantContext.getCurrentTenant();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("studentID", request.getStudentId());
        parameters.put("termID", termCode);
        parameters.put("year", request.getYear().longValue());
        parameters.put("tenantId", tenantId);
        parameters.put("Logo", safeLogo(resolveLogoPath(request.getLogoPath())));

        Map<String, String> branding = getTenantBranding();
        if (branding.containsKey("SchoolName")) {
            parameters.put("SchoolName", branding.get("SchoolName"));
        }
        if (branding.containsKey("Logo") && !StringUtils.hasText(request.getLogoPath())) {
            parameters.put("Logo", safeLogo(branding.get("Logo")));
        }
        if (branding.containsKey("SchoolAddress")) {
            parameters.put("SchoolAddress", branding.get("SchoolAddress"));
        }
        // Request-provided values override tenant branding.
        if (StringUtils.hasText(request.getSchoolName())) {
            parameters.put("SchoolName", request.getSchoolName());
        }

        String reportPath = path + FileTypeEnums.CBC_REPORT_CARD.getReportTypeString();
        File reportFile = new File(reportPath);
        if (!reportFile.exists()) {
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.setMessage("CBC report template not found at " + reportPath);
            return res;
        }

        try (InputStream reportStream = new FileInputStream(reportFile);
             Connection connection = DriverManager.getConnection(db, username, password)) {
            JasperReport compiledReport = JasperCompileManager.compileReport(reportStream);
            JasperPrint report = JasperFillManager.fillReport(compiledReport, parameters, connection);
            byte[] data = JasperExportManager.exportReportToPdf(report);
            res.setEntity(data);
            res.setStatusCode(HttpStatus.OK.value());
            res.setMessage("CBC report card generated successfully");
            return res;
        } catch (JRException e) {
            log.error("CBC report generation error: {}", e.getMessage(), e);
            res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            res.setMessage("Report generation error: " + e.getMessage());
            return res;
        } catch (Exception e) {
            log.error("Unexpected error generating CBC report: {}", e.getMessage(), e);
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
        // Prefer the explicitly-provided logo, then fall back to the packaged default.
        // Whatever we return is validated by the caller via safeLogo(...) so a bad
        // value never reaches Jasper (which would abort the whole PDF export).
        if (StringUtils.hasText(logoPath)) {
            return logoPath;
        }
        return path + "effort-schools-logo.jpg";
    }

    /**
     * Returns the given logo location only if it points to a resource that can
     * actually be decoded as an image (local file, classpath resource, or URL).
     * Otherwise returns {@code null} so the report simply omits the logo instead
     * of failing with "The byte array is not a recognized image format".
     */
    private String safeLogo(String logo) {
        if (!StringUtils.hasText(logo)) {
            return null;
        }
        try {
            java.awt.image.BufferedImage img;
            String value = logo.trim();
            if (value.startsWith("http://") || value.startsWith("https://")) {
                java.net.URL url = new java.net.URL(value);
                img = javax.imageio.ImageIO.read(url);
            } else {
                File file = new File(value);
                if (file.exists() && file.isFile()) {
                    img = javax.imageio.ImageIO.read(file);
                } else {
                    // Try classpath as a last resort (e.g. logos bundled in the jar).
                    try (InputStream in = getClass().getClassLoader()
                            .getResourceAsStream(value.startsWith("/") ? value.substring(1) : value)) {
                        img = in == null ? null : javax.imageio.ImageIO.read(in);
                    }
                }
            }
            if (img == null) {
                log.warn("Logo '{}' could not be decoded as an image; report will render without a logo.", logo);
                return null;
            }
            return logo;
        } catch (Exception e) {
            log.warn("Logo '{}' is not a usable image ({}); report will render without a logo.",
                    logo, e.getMessage());
            return null;
        }
    }
}
