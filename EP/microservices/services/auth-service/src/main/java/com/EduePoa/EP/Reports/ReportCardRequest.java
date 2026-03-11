package com.EduePoa.EP.Reports;

import lombok.Data;

@Data
public class ReportCardRequest {
    private Long studentId;
    private Long gradeId;
    private Integer termId; // 1=TERM_1, 2=TERM_2, 3=TERM_3
    private Integer year;
    private String logoPath;
    private String schoolName;
    private String schoolMotto;
    private String schoolContact;
}
