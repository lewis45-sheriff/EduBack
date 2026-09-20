package com.EduePoa.EP.academics.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk attendance submission for a single grade/class.
 * <p>
 * The date is determined server-side (current date) when marking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRequestDTO {
    private Long gradeId;
    private List<StudentAttendanceRequestDTO> students = new ArrayList<>();
}
