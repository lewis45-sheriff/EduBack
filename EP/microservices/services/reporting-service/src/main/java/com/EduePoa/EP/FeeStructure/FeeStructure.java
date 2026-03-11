package com.EduePoa.EP.FeeStructure;

import com.EduePoa.EP.FeeComponents.FeeComponents;
import com.EduePoa.EP.FeeStructure.FeeComponentConfig.FeeComponentConfig;
import com.EduePoa.EP.Grade.Grade;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class FeeStructure {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private int year;
    private  Double totalAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime datePosted;

    @OneToMany(mappedBy = "feeStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<FeeComponentConfig> TermComponents = new ArrayList<>();

    @OneToOne
    @JoinColumn(name = "grade_id")
    private Grade grade;



    @Column(name = "is_approved", nullable = false)
    private char isApproved = 'N'; // 'Y' = approved, 'N' = not approved

    @Column(name = "is_rejected", nullable = false)
    private char isRejected = 'N'; // 'Y' = rejected, 'N' = not rejected
    @Column(name = "approved_at")
    private LocalDate approvedAt;
    @Column
    private char isDeleted ='N';
    @Column
    private char deleted='N';


;


    @PrePersist
    public void prePersist() {
        if (datePosted == null) {
            datePosted = LocalDateTime.now();
        }
    }





}
