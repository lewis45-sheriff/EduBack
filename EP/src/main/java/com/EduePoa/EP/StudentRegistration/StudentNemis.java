package com.EduePoa.EP.StudentRegistration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "student_nemis")
public class StudentNemis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private Student student;

    private String upi;

    private String registrationIdentifierType;

    private String registrationIdentifierValue;

    private boolean queueSync = false;
}
