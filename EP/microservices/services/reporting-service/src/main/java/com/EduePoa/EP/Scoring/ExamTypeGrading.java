package com.EduePoa.EP.Scoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ExamTypeGrading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "exam_type_id", referencedColumnName = "id", nullable =false)
    private ExamType examType;

    @Column(nullable = false)
    private Double start;

    @Column(nullable = false)
    private Double end;

    @Column(nullable = true)
    private Double point;

    @Column(nullable = false)
    private String remarks;
}
