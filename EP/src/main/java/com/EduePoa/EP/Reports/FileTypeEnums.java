package com.EduePoa.EP.Reports;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum  FileTypeEnums {
    FEE_STRUCTURE("fee_structure", "fee_structure.jrxml"),
    FEE_STATEMENT("fee_statement", "fee_statement.jrxml"),
    PARENT_FEE_STRUCTURE("parent_fee_structure", "parent_fee_structure.jrxml"),
    PARENT_FEE_STATEMENT("parent_fee_statement", "parent_fee_statement.jrxml"),
    TERM_PERFORMANCE("term_performance_report", "term_performance_report.jrxml"),
    FEE_STRUCTURE_STUDENT("fee_structure_student", "fee_structure_student.jrxml"),
    FEE_STRUCTURE_STUDENT_TERM("fee_structure_student_term", "fee_structure_student_term.jrxml");

    private final String fileName;
    private final String reportTypeString;


    public static FileTypeEnums fromFileName(String fileName) {
        for (FileTypeEnums typeEnum : FileTypeEnums.values()) {
            if (typeEnum.getFileName().equalsIgnoreCase(fileName)) {
                return typeEnum;
            }
        }
        return null;  // Return null if no match is found
    }
}
