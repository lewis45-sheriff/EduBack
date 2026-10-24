package com.EduePoa.EP.HR.payroll.service;

import com.EduePoa.EP.HR.payroll.dto.response.PayslipLineResponse;
import com.EduePoa.EP.HR.payroll.dto.response.PayslipResponse;
import com.EduePoa.EP.HR.payroll.enums.PayslipLineType;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Renders a single payslip into a PDF using OpenPDF (bundled via JasperReports).
 * The PDF is built purely from the already-computed {@link PayslipResponse}, so it never
 * recalculates statutory figures — it formats the authoritative backend values for print.
 */
@Service
public class PayslipPdfService {

    private static final Color BRAND = new Color(0x25, 0x63, 0xEB);
    private static final Color HEADER_BG = new Color(0x25, 0x63, 0xEB);
    private static final Color SUBTOTAL_BG = new Color(0xF1, 0xF5, 0xF9);
    private static final Color NET_BG = new Color(0xEC, 0xFD, 0xF5);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
    private static final Font SUB = FontFactory.getFont(FontFactory.HELVETICA, 10, MUTED);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font GROUP_HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font CELL_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font NET_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(0x06, 0x5F, 0x46));
    private static final Font NET_VALUE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(0x06, 0x5F, 0x46));
    private static final Font FOOTER = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

    /** Display order and labels for the known payslip line groups. */
    private static final PayslipLineType[] GROUP_ORDER = {
            PayslipLineType.EARNING,
            PayslipLineType.STATUTORY,
            PayslipLineType.DEDUCTION,
            PayslipLineType.EMPLOYER_CONTRIB,
    };

    private static String groupLabel(PayslipLineType type) {
        return switch (type) {
            case EARNING -> "Earnings";
            case STATUTORY -> "Statutory Deductions";
            case DEDUCTION -> "Other Deductions";
            case EMPLOYER_CONTRIB -> "Employer Contributions";
        };
    }

    /**
     * Builds the payslip PDF. {@code schoolName} is optional and printed in the header when present.
     */
    public byte[] render(PayslipResponse slip, String schoolName) {
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(buildHeader(slip, schoolName));
            document.add(spacer(10));
            document.add(buildMeta(slip));
            document.add(spacer(14));

            for (PayslipLineType type : GROUP_ORDER) {
                List<PayslipLineResponse> lines = linesOfType(slip, type);
                if (!lines.isEmpty()) {
                    document.add(buildGroupTable(groupLabel(type), lines));
                    document.add(spacer(8));
                }
            }
            // Any unexpected/extra line types are shown rather than silently dropped.
            if (slip.getLines() != null) {
                for (PayslipLineResponse line : slip.getLines()) {
                    if (!isKnown(line.getLineType())) {
                        document.add(buildGroupTable("Other (" + line.getLineType() + ")", List.of(line)));
                        document.add(spacer(8));
                    }
                }
            }

            document.add(buildTotals(slip));
            document.add(spacer(8));
            document.add(buildNetPay(slip));
            document.add(spacer(18));
            document.add(buildFooter());

            document.close();
            return out.toByteArray();
        } catch (RuntimeException e) {
            if (document.isOpen()) {
                document.close();
            }
            throw new RuntimeException("Failed to render payslip PDF: " + e.getMessage(), e);
        }
    }

    public String suggestedFileName(PayslipResponse slip) {
        String who = slip.getStaffName() != null && !slip.getStaffName().isBlank()
                ? slip.getStaffName().trim().replaceAll("[^A-Za-z0-9]+", "_")
                : "staff_" + slip.getStaffId();
        return "payslip_" + who + "_" + period(slip).replace(" ", "_") + ".pdf";
    }

    // ---- sections -----------------------------------------------------------

    private Element buildHeader(PayslipResponse slip, String schoolName) {
        PdfPTable table = fullWidth(2);
        table.setWidths(new float[]{2.4f, 1.6f});

        PdfPCell left = noBorder();
        Paragraph title = new Paragraph("Payslip", H1);
        left.addElement(title);
        String who = slip.getStaffName() != null ? slip.getStaffName() : "Staff #" + slip.getStaffId();
        left.addElement(new Paragraph(who + "  \u00b7  " + period(slip), SUB));
        table.addCell(left);

        PdfPCell right = noBorder();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (schoolName != null && !schoolName.isBlank()) {
            Paragraph org = new Paragraph(schoolName, VALUE);
            org.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(org);
        }
        Paragraph gen = new Paragraph("Generated " + today(), SUB);
        gen.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(gen);
        table.addCell(right);

        return table;
    }

    private Element buildMeta(PayslipResponse slip) {
        PdfPTable table = fullWidth(4);
        table.setWidths(new float[]{1f, 1f, 1f, 1f});

        addMeta(table, "Employee No.", orDash(slip.getEmployeeNumber()));
        addMeta(table, "Period", period(slip));
        addMeta(table, "Basic pay", money(slip.getBasicPay()));
        addMeta(table, "Gross pay", money(slip.getGrossPay()));

        addMeta(table, "Taxable income", money(slip.getTaxableIncome()));
        addMeta(table, "Personal relief", money(slip.getPersonalRelief()));
        addMeta(table, "Insurance relief", money(slip.getInsuranceRelief()));
        addMeta(table, "Proration", slip.isProrated()
                ? "Prorated " + nz(slip.getEligibleDays()) + "/" + nz(slip.getTotalDays()) + " days"
                : "Full month");

        return table;
    }

    private PdfPTable buildGroupTable(String title, List<PayslipLineResponse> lines) {
        PdfPTable table = fullWidth(2);
        table.setWidths(new float[]{3f, 1.2f});

        PdfPCell head = new PdfPCell(new Phrase(title, GROUP_HEAD));
        head.setColspan(2);
        head.setBackgroundColor(HEADER_BG);
        head.setPadding(6);
        head.setBorderColor(HEADER_BG);
        table.addCell(head);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (PayslipLineResponse line : lines) {
            table.addCell(bodyCell(line.getName(), CELL, Element.ALIGN_LEFT));
            table.addCell(bodyCell(money(line.getAmount()), CELL, Element.ALIGN_RIGHT));
            subtotal = subtotal.add(line.getAmount() != null ? line.getAmount() : BigDecimal.ZERO);
        }

        PdfPCell subLabel = bodyCell("Subtotal", CELL_BOLD, Element.ALIGN_LEFT);
        subLabel.setBackgroundColor(SUBTOTAL_BG);
        PdfPCell subValue = bodyCell(money(subtotal), CELL_BOLD, Element.ALIGN_RIGHT);
        subValue.setBackgroundColor(SUBTOTAL_BG);
        table.addCell(subLabel);
        table.addCell(subValue);

        return table;
    }

    private PdfPTable buildTotals(PayslipResponse slip) {
        PdfPTable table = fullWidth(2);
        table.setWidths(new float[]{3f, 1.2f});
        table.addCell(bodyCell("Total employee deductions", CELL, Element.ALIGN_LEFT));
        table.addCell(bodyCell(money(slip.getTotalEmployeeDeductions()), CELL_BOLD, Element.ALIGN_RIGHT));
        table.addCell(bodyCell("Total employer contributions", CELL, Element.ALIGN_LEFT));
        table.addCell(bodyCell(money(slip.getTotalEmployerContributions()), CELL_BOLD, Element.ALIGN_RIGHT));
        return table;
    }

    private PdfPTable buildNetPay(PayslipResponse slip) {
        PdfPTable table = fullWidth(2);
        table.setWidths(new float[]{3f, 1.2f});

        PdfPCell label = new PdfPCell(new Phrase("Net Pay", NET_LABEL));
        label.setBackgroundColor(NET_BG);
        label.setBorderColor(new Color(0x10, 0xB9, 0x81));
        label.setPadding(10);
        label.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell value = new PdfPCell(new Phrase(money(slip.getNetPay()), NET_VALUE));
        value.setBackgroundColor(NET_BG);
        value.setBorderColor(new Color(0x10, 0xB9, 0x81));
        value.setPadding(10);
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(label);
        table.addCell(value);
        return table;
    }

    private Element buildFooter() {
        Paragraph p = new Paragraph(
                "This document is computer generated. Figures are system-computed and recorded for payroll purposes only.",
                FOOTER);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    // ---- helpers ------------------------------------------------------------

    private List<PayslipLineResponse> linesOfType(PayslipResponse slip, PayslipLineType type) {
        if (slip.getLines() == null) return List.of();
        return slip.getLines().stream().filter(l -> l.getLineType() == type).toList();
    }

    private boolean isKnown(PayslipLineType type) {
        for (PayslipLineType t : GROUP_ORDER) {
            if (t == type) return true;
        }
        return false;
    }

    private void addMeta(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(new Color(0xF0, 0xF0, 0xF0));
        cell.setPadding(5);
        cell.addElement(new Paragraph(label, LABEL));
        cell.addElement(new Paragraph(value, VALUE));
        table.addCell(cell);
    }

    private PdfPCell bodyCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(0xEE, 0xF2, 0xF7));
        return cell;
    }

    private PdfPCell noBorder() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        return cell;
    }

    private PdfPTable fullWidth(int cols) {
        PdfPTable table = new PdfPTable(cols);
        table.setWidthPercentage(100);
        table.getDefaultCell().setBorderColor(new Color(0xEE, 0xF2, 0xF7));
        return table;
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    private String period(PayslipResponse slip) {
        String month = (slip.getPeriodMonth() != null && slip.getPeriodMonth() >= 1 && slip.getPeriodMonth() <= 12)
                ? java.time.Month.of(slip.getPeriodMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                : String.valueOf(slip.getPeriodMonth());
        return month + " " + (slip.getPeriodYear() != null ? slip.getPeriodYear() : "");
    }

    private String today() {
        LocalDate d = LocalDate.now();
        return d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + d.getYear();
    }

    private String money(BigDecimal amount) {
        BigDecimal v = amount != null ? amount : BigDecimal.ZERO;
        return "KES " + String.format(Locale.ENGLISH, "%,.2f", v);
    }

    private String orDash(String s) {
        return s != null && !s.isBlank() ? s : "-";
    }

    private int nz(Integer i) {
        return i != null ? i : 0;
    }
}
