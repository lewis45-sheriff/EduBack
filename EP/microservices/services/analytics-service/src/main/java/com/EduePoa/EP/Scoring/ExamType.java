package com.EduePoa.EP.Scoring;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class ExamType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private Long maxScore;

    @Column(nullable = false)
    private Long minScore;
}
