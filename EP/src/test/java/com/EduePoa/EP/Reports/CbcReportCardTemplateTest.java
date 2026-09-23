package com.EduePoa.EP.Reports;

import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiles the CBC report-card .jrxml at build time so a malformed template fails the build
 * instead of only failing at runtime when a report is requested. No DB or Spring context needed.
 */
class CbcReportCardTemplateTest {

    @Test
    @DisplayName("cbc_report_card.jrxml compiles successfully")
    void cbcReportCardTemplateCompiles() throws Exception {
        File template = new File("JasperReports/jrxmls/cbc_report_card.jrxml");
        assertThat(template).as("CBC report template must exist").exists();
        try (InputStream in = new FileInputStream(template)) {
            // Throws JRException if the template is invalid.
            var compiled = JasperCompileManager.compileReport(in);
            assertThat(compiled).isNotNull();
            assertThat(compiled.getName()).isEqualTo("cbc_report_card");
        }
    }
}
