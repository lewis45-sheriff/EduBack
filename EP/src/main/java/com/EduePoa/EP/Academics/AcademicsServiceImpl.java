package com.EduePoa.EP.Academics;

import com.EduePoa.EP.Academics.Attendance.Attendance;
import com.EduePoa.EP.Academics.Attendance.AttendanceRepository;
import com.EduePoa.EP.Academics.Attendance.AttendnanceDetail;
import com.EduePoa.EP.Academics.Attendance.Requests.AttendanceRequestDTO;
import com.EduePoa.EP.Academics.Attendance.Requests.StudentAttendanceRequestDTO;
import com.EduePoa.EP.Academics.Attendance.Responses.AttendanceResponseDTO;
import com.EduePoa.EP.Academics.Attendance.Responses.StudentAttendanceResponseDTO;
import com.EduePoa.EP.Authentication.AuditLogs.AuditService;
import com.EduePoa.EP.Authentication.Enum.AttendanceStatus;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.Grade.GradeRepository;
import com.EduePoa.EP.StudentRegistration.Student;
import com.EduePoa.EP.StudentRegistration.StudentRepository;
import com.EduePoa.EP.Utils.CustomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AcademicsServiceImpl implements AcademicsService {
    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AuditService auditService;

    @Override
    public CustomResponse<?> markAttendance(AttendanceRequestDTO attendanceRequestDTO) {
        CustomResponse<AttendanceResponseDTO> response = new CustomResponse<>();
        try {

            Grade grade = gradeRepository.findById(attendanceRequestDTO.getGradeId())
                    .orElseThrow(() -> new RuntimeException("Grade not found"));
            LocalDate today = LocalDate.now();

            boolean alreadyMarked = attendanceRepository
                    .findByGradeIdAndAttendanceDate(grade.getId(), today)
                    .isPresent();

            if (alreadyMarked) {
                response.setMessage("Attendance has already been marked for this grade today");
                response.setStatusCode(HttpStatus.CONFLICT.value());
                response.setEntity(null);
                return response;
            }

            Attendance attendance = new Attendance();
            attendance.setGrade(grade);
            attendance.setDateTime(LocalDateTime.now());
            attendance.setAttendanceDate(LocalDate.now());

            List<AttendnanceDetail> details = new ArrayList<>();

            for (StudentAttendanceRequestDTO sDto : attendanceRequestDTO.getStudents()) {

                Student student = studentRepository.findById(sDto.getStudentId())
                        .orElseThrow(() -> new RuntimeException("Student not found: " + sDto.getStudentId()));

                AttendnanceDetail detail = new AttendnanceDetail();
                detail.setAttendance(attendance);
                detail.setStudent(student);
                detail.setStatus(AttendanceStatus.valueOf(sDto.getStatus()));
                detail.setRemarks(sDto.getRemarks());

                details.add(detail);
            }

            attendance.setAttendanceDetails(details);

            Attendance savedAttendance = attendanceRepository.save(attendance);

            AttendanceResponseDTO dto = mapToResponseDTO(savedAttendance);

            response.setMessage("Attendance marked successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dto);
            auditService.log("ACADEMICS", "Marked attendance for grade:", grade.getName(), "ID:",
                    String.valueOf(savedAttendance.getId()), "students:", String.valueOf(details.size()));

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAllAttendance() {
        CustomResponse<List<AttendanceResponseDTO>> response = new CustomResponse<>();
        try {

            List<Attendance> attendanceList = attendanceRepository.findAll();

            List<AttendanceResponseDTO> dtoList = attendanceList.stream()
                    .map(this::mapToResponseDTO)
                    .toList();

            response.setMessage("Attendance records retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dtoList);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> getAttendanceById(Long id) {
        CustomResponse<AttendanceResponseDTO> response = new CustomResponse<>();
        try {

            Attendance attendance = attendanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Attendance not found"));

            AttendanceResponseDTO dto = mapToResponseDTO(attendance);

            response.setMessage("Attendance record retrieved successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dto);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> updateAttendance(Long id, AttendanceRequestDTO attendanceRequestDTO) {
        CustomResponse<AttendanceResponseDTO> response = new CustomResponse<>();
        try {

            Attendance attendance = attendanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Attendance not found"));

            Grade grade = gradeRepository.findById(attendanceRequestDTO.getGradeId())
                    .orElseThrow(() -> new RuntimeException("Grade not found"));

            attendance.setGrade(grade);

            // Clear old attendance details
            attendance.getAttendanceDetails().clear();

            List<AttendnanceDetail> details = new ArrayList<>();

            for (StudentAttendanceRequestDTO sDto : attendanceRequestDTO.getStudents()) {

                Student student = studentRepository.findById(sDto.getStudentId())
                        .orElseThrow(() -> new RuntimeException("Student not found: " + sDto.getStudentId()));

                AttendnanceDetail detail = new AttendnanceDetail();
                detail.setAttendance(attendance);
                detail.setStudent(student);
                detail.setStatus(AttendanceStatus.valueOf(sDto.getStatus()));
                detail.setRemarks(sDto.getRemarks());

                details.add(detail);
            }

            attendance.setAttendanceDetails(details);

            Attendance updatedAttendance = attendanceRepository.save(attendance);

            AttendanceResponseDTO dto = mapToResponseDTO(updatedAttendance);

            response.setMessage("Attendance updated successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(dto);
            auditService.log("ACADEMICS", "Updated attendance ID:", String.valueOf(id), "for grade:",
                    attendance.getGrade().getName());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    @Override
    public CustomResponse<?> deleteAttendance(Long id) {
        CustomResponse<?> response = new CustomResponse<>();
        try {

            Attendance attendance = attendanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Attendance not found"));

            attendanceRepository.delete(attendance);

            response.setMessage("Attendance deleted successfully");
            response.setStatusCode(HttpStatus.OK.value());
            response.setEntity(null);
            auditService.log("ACADEMICS", "Deleted attendance with ID:", String.valueOf(id));

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setEntity(null);
        }
        return response;
    }

    private AttendanceResponseDTO mapToResponseDTO(Attendance attendance) {

        AttendanceResponseDTO dto = new AttendanceResponseDTO();
        dto.setId(attendance.getId());
        dto.setGradeId(attendance.getGrade().getId());
        dto.setGradeName(attendance.getGrade().getName());
        dto.setDateTime(attendance.getDateTime());

        List<StudentAttendanceResponseDTO> students = attendance.getAttendanceDetails().stream().map(detail -> {
            StudentAttendanceResponseDTO sDto = new StudentAttendanceResponseDTO();
            sDto.setStudentId(detail.getStudent().getId());
            sDto.setStudentName(detail.getStudent().getFirstName() + " " + detail.getStudent().getLastName());
            sDto.setStatus(detail.getStatus().name());
            sDto.setRemarks(detail.getRemarks());
            return sDto;
        }).toList();

        dto.setStudents(students);

        return dto;
    }

}
