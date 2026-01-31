package com.EduePoa.EP.Academics.Attendance;

import com.EduePoa.EP.Authentication.Enum.AttendanceStatus;
import com.EduePoa.EP.Grade.Grade;
import com.EduePoa.EP.StudentRegistration.Student;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student_attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
            name = "attendance_students",
            joinColumns = @JoinColumn(name = "attendance_id"),
            inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    private List<Student> students;

    // Relationship with Grade (Many attendance records belong to one grade)
    @ManyToOne
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @Column(nullable = false)
    private LocalDateTime dateTime;
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;
    @OneToMany(mappedBy = "attendance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendnanceDetail> attendanceDetails;
}
