package com.EduePoa.EP.Academics;

import com.EduePoa.EP.Academics.Attendance.Requests.AttendanceRequestDTO;
import com.EduePoa.EP.Utils.CustomResponse;

public interface AcademicsService {
    CustomResponse<?> markAttendance(AttendanceRequestDTO attendanceRequestDTO);
    CustomResponse<?> getAllAttendance();

    CustomResponse<?> getAttendanceById(Long id);

    CustomResponse<?> updateAttendance(Long id, AttendanceRequestDTO attendanceRequestDTO);

    CustomResponse<?> deleteAttendance(Long id);
}
