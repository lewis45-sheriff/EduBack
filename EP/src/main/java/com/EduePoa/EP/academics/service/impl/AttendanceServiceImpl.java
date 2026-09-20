package com.EduePoa.EP.academics.service.impl;

import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.AttendanceStatus;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.academics.dto.request.AttendanceRequestDTO;
import com.EduePoa.EP.academics.dto.request.StudentAttendanceRequestDTO;
import com.EduePoa.EP.academics.dto.response.AttendanceRecordDto;
import com.EduePoa.EP.academics.dto.response.AttendanceSummaryDto;
import com.EduePoa.EP.academics.dto.response.ClassAttendanceDto;
import com.EduePoa.EP.academics.dto.response.StudentAttendanceRowDto;
import com.EduePoa.EP.academics.entity.Attendance;
import com.EduePoa.EP.academics.repository.AttendanceRepository;
import com.EduePoa.EP.academics.service.AttendanceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final AuditService auditService;

    private static final String AUDIT_MODULE = "ATTENDANCE";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    @Transactional
    public String markAttendance(AttendanceRequestDTO request) {
        if (request == null || request.getGradeId() == null) {
            throw new IllegalArgumentException("gradeId is required");
        }
        if (request.getStudents() == null || request.getStudents().isEmpty()) {
            throw new IllegalArgumentException("At least one student attendance entry is required");
        }

        Grade grade = gradeRepository.findById(request.getGradeId())
                .orElseThrow(() -> new EntityNotFoundException("Grade not found with id: " + request.getGradeId()));

        LocalDate today = LocalDate.now();
        String markedBy = currentUserEmail();

        int created = 0;
        int updated = 0;

        for (StudentAttendanceRequestDTO entry : request.getStudents()) {
            if (entry.getStudentId() == null || entry.getStatus() == null) {
                throw new IllegalArgumentException("Each student entry requires studentId and status");
            }

            Student student = studentRepository.findById(entry.getStudentId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Student not found with id: " + entry.getStudentId()));

            // Validate the student belongs to the submitted grade.
            if (student.getGrade() == null || !student.getGrade().getId().equals(grade.getId())) {
                throw new IllegalArgumentException(
                        "Student " + entry.getStudentId() + " does not belong to grade " + grade.getId());
            }

            Optional<Attendance> existing = attendanceRepository.findByStudentAndDate(student, today);
            Attendance attendance = existing.orElseGet(Attendance::new);

            boolean isNew = existing.isEmpty();
            attendance.setStudent(student);
            attendance.setGrade(grade);
            attendance.setDate(today);
            attendance.setStatus(entry.getStatus());
            attendance.setRemarks(entry.getRemarks());
            attendance.setMarkedBy(markedBy);
            // Record a check-in time when present or late; clear otherwise.
            if (entry.getStatus() == AttendanceStatus.PRESENT || entry.getStatus() == AttendanceStatus.LATE) {
                if (attendance.getCheckInTime() == null) {
                    attendance.setCheckInTime(LocalTime.now());
                }
            } else {
                attendance.setCheckInTime(null);
            }

            attendanceRepository.save(attendance);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        log.info("Attendance marked for grade {} on {}: {} created, {} updated",
                grade.getId(), today, created, updated);

        auditService.log(AUDIT_MODULE, "Marked attendance for grade:", grade.getName(),
                "on", today.toString(), "-", String.valueOf(created), "new,",
                String.valueOf(updated), "updated");

        return "Attendance recorded successfully (" + created + " new, " + updated + " updated)";
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceRecordDto> getAllAttendance(LocalDate date, Long gradeId) {
        List<Attendance> records;

        if (gradeId != null) {
            Grade grade = gradeRepository.findById(gradeId)
                    .orElseThrow(() -> new EntityNotFoundException("Grade not found with id: " + gradeId));
            records = (date != null)
                    ? attendanceRepository.findByGradeAndDate(grade, date)
                    : attendanceRepository.findByGrade(grade);
        } else if (date != null) {
            records = attendanceRepository.findByDate(date);
        } else {
            records = attendanceRepository.findAll();
        }

        List<AttendanceRecordDto> result = new ArrayList<>();
        for (Attendance a : records) {
            result.add(toRecordDto(a));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceRecordDto getAttendanceById(Long id) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found with id: " + id));
        return toRecordDto(attendance);
    }

    @Override
    @Transactional
    public String updateAttendance(Long id, AttendanceRequestDTO request) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found with id: " + id));

        if (request == null || request.getStudents() == null || request.getStudents().isEmpty()) {
            throw new IllegalArgumentException("At least one student attendance entry is required");
        }

        // For a single-record update the frontend sends one entry; use the first
        // entry that matches this record's student, else the first entry.
        StudentAttendanceRequestDTO entry = request.getStudents().stream()
                .filter(e -> e.getStudentId() != null
                        && attendance.getStudent() != null
                        && e.getStudentId().equals(attendance.getStudent().getId()))
                .findFirst()
                .orElse(request.getStudents().get(0));

        if (entry.getStatus() == null) {
            throw new IllegalArgumentException("status is required");
        }

        attendance.setStatus(entry.getStatus());
        attendance.setRemarks(entry.getRemarks());
        attendance.setMarkedBy(currentUserEmail());
        if (entry.getStatus() == AttendanceStatus.PRESENT || entry.getStatus() == AttendanceStatus.LATE) {
            if (attendance.getCheckInTime() == null) {
                attendance.setCheckInTime(LocalTime.now());
            }
        } else {
            attendance.setCheckInTime(null);
        }

        attendanceRepository.save(attendance);

        auditService.log(AUDIT_MODULE, "Updated attendance record ID:", String.valueOf(id),
                "for student:", attendance.getStudent() != null ? attendance.getStudent().getAdmissionNumber() : "",
                "status:", entry.getStatus().name());

        return "Attendance updated successfully";
    }

    @Override
    @Transactional
    public String deleteAttendance(Long id) {
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attendance record not found with id: " + id));

        String admissionNumber = attendance.getStudent() != null
                ? attendance.getStudent().getAdmissionNumber() : "";
        String date = attendance.getDate() != null ? attendance.getDate().toString() : "";

        attendanceRepository.delete(attendance);

        auditService.log(AUDIT_MODULE, "Deleted attendance record ID:", String.valueOf(id),
                "for student:", admissionNumber, "on", date);

        return "Attendance deleted successfully";
    }

    @Override
    @Transactional(readOnly = true)
    public ClassAttendanceDto getClassAttendance(Long gradeId, LocalDate date) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new EntityNotFoundException("Grade not found with id: " + gradeId));

        LocalDate target = (date != null) ? date : LocalDate.now();
        List<Student> students = studentRepository.findByGradeId(gradeId);
        List<Attendance> marked = attendanceRepository.findByGradeAndDate(grade, target);

        List<StudentAttendanceRowDto> rows = new ArrayList<>();
        int markedCount = 0;

        for (Student student : students) {
            Optional<Attendance> record = marked.stream()
                    .filter(a -> a.getStudent() != null && a.getStudent().getId().equals(student.getId()))
                    .findFirst();

            StudentAttendanceRowDto row = new StudentAttendanceRowDto();
            row.setStudentId(student.getId());
            row.setStudentName(fullName(student));
            row.setAdmissionNumber(student.getAdmissionNumber());

            if (record.isPresent()) {
                Attendance a = record.get();
                row.setAttendanceId(a.getId());
                row.setStatus(a.getStatus() != null ? a.getStatus().name() : null);
                row.setCheckInTime(formatTime(a.getCheckInTime()));
                row.setRemarks(a.getRemarks());
                row.setMarked(true);
                markedCount++;
            } else {
                row.setMarked(false);
            }
            rows.add(row);
        }

        ClassAttendanceDto dto = new ClassAttendanceDto();
        dto.setGradeId(grade.getId());
        dto.setGradeName(grade.getName());
        dto.setDate(target.toString());
        dto.setTotalStudents(students.size());
        dto.setMarkedCount(markedCount);
        dto.setStudents(rows);
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceSummaryDto> getSummary(Long gradeId, LocalDate from, LocalDate to) {
        LocalDate start = (from != null) ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = (to != null) ? to : LocalDate.now();

        List<Attendance> records;
        List<Student> students;

        if (gradeId != null) {
            // Per-grade summary
            Grade grade = gradeRepository.findById(gradeId)
                    .orElseThrow(() -> new EntityNotFoundException("Grade not found with id: " + gradeId));
            records = attendanceRepository.findByGradeAndDateBetween(grade, start, end);
            students = studentRepository.findByGradeId(gradeId);
        } else {
            // Whole-school summary across all grades
            records = attendanceRepository.findByDateBetween(start, end);
            students = studentRepository.findAll();
        }

        List<AttendanceSummaryDto> summaries = new ArrayList<>();
        for (Student student : students) {
            long present = 0, absent = 0, late = 0, excused = 0;
            for (Attendance a : records) {
                if (a.getStudent() == null || !a.getStudent().getId().equals(student.getId())) {
                    continue;
                }
                switch (a.getStatus()) {
                    case PRESENT -> present++;
                    case ABSENT -> absent++;
                    case LATE -> late++;
                    case EXCUSED -> excused++;
                }
            }
            long total = present + absent + late + excused;
            summaries.add(new AttendanceSummaryDto(
                    student.getId(), fullName(student), student.getAdmissionNumber(),
                    present, absent, late, excused, total));
        }
        return summaries;
    }

    // --- helpers -----------------------------------------------------------

    private AttendanceRecordDto toRecordDto(Attendance a) {
        Student student = a.getStudent();
        return new AttendanceRecordDto(
                a.getId(),
                student != null ? student.getId() : null,
                student != null ? fullName(student) : null,
                student != null ? student.getAdmissionNumber() : null,
                a.getDate() != null ? a.getDate().toString() : null,
                a.getStatus() != null ? a.getStatus().name() : null,
                formatTime(a.getCheckInTime()),
                a.getRemarks(),
                a.getMarkedBy());
    }

    private String fullName(Student student) {
        StringBuilder sb = new StringBuilder();
        if (student.getFirstName() != null) {
            sb.append(student.getFirstName());
        }
        if (student.getLastName() != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(student.getLastName());
        }
        return sb.toString().trim();
    }

    private String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMAT) : null;
    }

    private String currentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "SYSTEM";
    }
}
