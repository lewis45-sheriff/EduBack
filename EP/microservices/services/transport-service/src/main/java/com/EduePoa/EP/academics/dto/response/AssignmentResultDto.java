package com.EduePoa.EP.academics.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class AssignmentResultDto {
    private Long gradeId;
    private String gradeName;
    private Integer year;
    private Integer totalSubjectsAssigned;
    private List<String> assignedSubjectNames;
}
