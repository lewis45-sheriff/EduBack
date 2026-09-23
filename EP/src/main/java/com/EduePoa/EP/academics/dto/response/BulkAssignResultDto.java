package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a bulk teacher-assignment request: which entries were created and which were skipped
 * (duplicates or invalid), so the caller gets partial-success visibility.
 */
@Data
public class BulkAssignResultDto {
    private int created;
    private int skipped;
    private List<TeacherSubjectAssignmentDto> assignments = new ArrayList<>();
    private List<String> skippedReasons = new ArrayList<>();
}
