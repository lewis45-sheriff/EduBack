package com.EduePoa.EP.Grade;

import com.EduePoa.EP.StudentRegistration.Student;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Entity
@Data
@Where(clause = "deleted_flag = 'N'")

public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    private Integer gradeNumber;

    @Column(unique = true)
    private String name;

    @JsonIgnore
    private char deletedFlag = 'N';

//    @OneToMany(mappedBy = "grade", cascade = CascadeType.ALL, orphanRemoval = true)
//    @JsonManagedReference
//    @JsonIgnore
//    private List<GradeStream> gradeStreams = new ArrayList<>();
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JsonIgnore
//    @JoinColumn(name = "school_id", referencedColumnName = "id")
//    private School school;


//    @OneToMany(mappedBy = "grade", cascade = CascadeType.ALL, orphanRemoval = true)
//    @JsonIgnore
//    private List<Student> students = new ArrayList<>();

}
