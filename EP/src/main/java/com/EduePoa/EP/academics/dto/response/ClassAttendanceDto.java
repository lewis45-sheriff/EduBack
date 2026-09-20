package com.EduePoa.EP.academics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Roster for a grade on a specific date, used to pre-fill the daily
 * marking screen. Includes students that have not yet been marked.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassAttendanceDto {
    private Long gradeId;
    private String gradeName;
    private String date;
    private int totalStudents;
    private int markedCount;
    private List<StudentAttendanceRowDto> students = new ArrayList<>();
}
