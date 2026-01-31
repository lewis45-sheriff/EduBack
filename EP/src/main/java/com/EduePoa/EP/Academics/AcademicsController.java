package com.EduePoa.EP.Academics;

import com.EduePoa.EP.Academics.Attendance.Requests.AttendanceRequestDTO;
import com.EduePoa.EP.Analytics.Response.AnalyticsResponse;
import com.EduePoa.EP.Authentication.Role.Request.RoleRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/academics")
@RequiredArgsConstructor
public class AcademicsController {
    private final AcademicsService academicsService;

    @PostMapping("mark-attendance")
    ResponseEntity<?> markAttendance(@RequestBody AttendanceRequestDTO attendanceRequestDTO){
        var response =  academicsService. markAttendance(attendanceRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("attendance")
    ResponseEntity<?> getAllAttendance() {
        var response = academicsService.getAllAttendance();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("attendance/{id}")
    ResponseEntity<?> getAttendanceById(@PathVariable Long id) {
        var response = academicsService.getAttendanceById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping("attendance/{id}")
    ResponseEntity<?> updateAttendance(@PathVariable Long id, @RequestBody AttendanceRequestDTO attendanceRequestDTO) {
        var response = academicsService.updateAttendance(id, attendanceRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @DeleteMapping("attendance/{id}")
    ResponseEntity<?> deleteAttendance(@PathVariable Long id) {
        var response = academicsService.deleteAttendance(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }


}
