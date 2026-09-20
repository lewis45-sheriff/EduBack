package com.EduePoa.EP.academics.service;

import com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO;
import com.EduePoa.EP.academics.dto.response.AttendanceRecordDto;
import com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto;
import com.EduePoa.EP.academics.dto.response.ClassAttendanceDto;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceService {

    /**
     * Marks (or upserts) attendance for the given grade's students on the
     * current date. Returns a human-readable message summarising the result.
     */
    String markAttendance(AttendanceRequestDTO request);

    /**
     * Returns all attendance records, optionally filtered by date and/or grade.
     */
    List<AttendanceRecordDto> getAllAttendance(LocalDate date, Long gradeId);

    AttendanceRecordDto getAttendanceById(Long id);

    /**
     * Updates a single attendance record identified by {@code id}.
     */
    String updateAttendance(Long id, AttendanceRequestDTO request);

    String deleteAttendance(Long id);

    /**
     * Roster for a grade on a specific date, including unmarked students.
     */
    ClassAttendanceDto getClassAttendance(Long gradeId, LocalDate date);

    /**
     * Per-student tallies for a grade over a date range.
     */
    List<AttendanceSummaryDto> getSummary(Long gradeId, LocalDate from, LocalDate to);
}
