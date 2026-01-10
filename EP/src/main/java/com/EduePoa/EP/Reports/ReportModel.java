package com.EduePoa.EP.Reports;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReportModel {
    BigDecimal studentID;
    BigDecimal parentId;
    String term;
    BigDecimal year;
    BigDecimal gradeId;
    String fileName;
}

