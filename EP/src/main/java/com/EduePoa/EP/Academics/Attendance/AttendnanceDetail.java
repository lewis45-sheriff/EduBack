package com.EduePoa.EP.Academics.Attendance;

import com.EduePoa.EP.Authentication.Enum.AttendanceStatus;
import com.EduePoa.EP.StudentRegistration.Student;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "attendance_details")
public class AttendnanceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    private String remarks;
}
