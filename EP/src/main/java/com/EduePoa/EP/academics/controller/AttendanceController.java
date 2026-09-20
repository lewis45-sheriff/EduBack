package com.EduePoa.EP.academics.controller;

import com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO;
import com.EduePoa.EP.academics.dto.response.AttendanceRecordDto;
import com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto;
import com.EduePoa.EP.academics.dto.response.ClassAttendanceDto;
import com.EduePoa.EP.academics.service.AttendanceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Daily student attendance endpoints.
 * <p>
 * Response envelopes intentionally match the existing frontend contract:
 * {@code {"message": "..."}} for writes, {@code {"attendance": [...]}} for
 * the list, and a bare {@link AttendanceRecordDto} for get-by-id.
 */
@RestController
@RequestMapping("api/v1/academics")
@RequiredArgsConstructor
@Slf4j
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/mark-attendance")
    @PreAuthorize("hasPermission(null, 'attendance:mark')")
    public ResponseEntity<?> markAttendance(@RequestBody AttendanceRequestDTO request) {
        try {
            String message = attendanceService.markAttendance(request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error marking attendance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error marking attendance: " + e.getMessage()));
        }
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasPermission(null, 'attendance:read')")
    public ResponseEntity<?> getAttendance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long gradeId) {
        try {
            List<AttendanceRecordDto> attendance = attendanceService.getAllAttendance(date, gradeId);
            return ResponseEntity.ok(Map.of("attendance", attendance));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching attendance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error fetching attendance: " + e.getMessage()));
        }
    }

    @GetMapping("/attendance/{id}")
    @PreAuthorize("hasPermission(null, 'attendance:read')")
    public ResponseEntity<?> getAttendanceById(@PathVariable Long id) {
        try {
            AttendanceRecordDto record = attendanceService.getAttendanceById(id);
            return ResponseEntity.ok(record);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching attendance record {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error fetching attendance record: " + e.getMessage()));
        }
    }

    @PutMapping("/attendance/{id}")
    @PreAuthorize("hasPermission(null, 'attendance:mark')")
    public ResponseEntity<?> updateAttendance(@PathVariable Long id, @RequestBody AttendanceRequestDTO request) {
        try {
            String message = attendanceService.updateAttendance(id, request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating attendance record {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error updating attendance: " + e.getMessage()));
        }
    }

    @DeleteMapping("/attendance/{id}")
    @PreAuthorize("hasPermission(null, 'attendance:mark')")
    public ResponseEntity<?> deleteAttendance(@PathVariable Long id) {
        try {
            String message = attendanceService.deleteAttendance(id);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting attendance record {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error deleting attendance: " + e.getMessage()));
        }
    }

    @GetMapping("/attendance/class/{gradeId}")
    @PreAuthorize("hasPermission(null, 'attendance:read')")
    public ResponseEntity<?> getClassAttendance(
            @PathVariable Long gradeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            ClassAttendanceDto roster = attendanceService.getClassAttendance(gradeId, date);
            return ResponseEntity.ok(roster);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching class attendance for grade {}", gradeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error fetching class attendance: " + e.getMessage()));
        }
    }

    @GetMapping("/attendance/summary")
    @PreAuthorize("hasPermission(null, 'attendance:read')")
    public ResponseEntity<?> getSummary(
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            List<AttendanceSummaryDto> summary = attendanceService.getSummary(gradeId, from, to);
            return ResponseEntity.ok(Map.of("summary", summary));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching attendance summary for grade {}", gradeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error fetching attendance summary: " + e.getMessage()));
        }
    }
}
